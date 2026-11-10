package com.rrvgreenbasket.store.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class InternalServiceSecurityFilter extends OncePerRequestFilter {

    private final S2SSignatureUtil signatureUtil;
    private final NonceReplayCache nonceCache;
    private final String internalSecret;
    private final List<String> authorizedServices;

    public InternalServiceSecurityFilter(
            S2SSignatureUtil signatureUtil,
            NonceReplayCache nonceCache,
            @Value("${app.security.internal-service-secret:c8f7e2d1a5b49382716054321fedcba9876543210abcdef0123456789abcdef0}") String internalSecret,
            @Value("${app.security.authorized-internal-services:api-gateway,auth-service,order-service,cart-service,customer-service,product-service}") String authorizedServices) {
        this.signatureUtil = signatureUtil;
        this.nonceCache = nonceCache;
        this.internalSecret = internalSecret;
        this.authorizedServices = Arrays.asList(authorizedServices.split(","));
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String serviceId = request.getHeader("X-Internal-Service-Id");
        String timestampStr = request.getHeader("X-Internal-Timestamp");
        String nonce = request.getHeader("X-Internal-Nonce");
        String signature = request.getHeader("X-Internal-Signature");

        if (StringUtils.hasText(serviceId) && StringUtils.hasText(timestampStr)
                && StringUtils.hasText(nonce) && StringUtils.hasText(signature)) {

            try {
                long timestamp = Long.parseLong(timestampStr);
                long now = System.currentTimeMillis();

                if (Math.abs(now - timestamp) > 300000) {
                    log.warn("Internal S2S request timestamp expired: serviceId={}, timestamp={}", serviceId, timestamp);
                    filterChain.doFilter(request, response);
                    return;
                }

                if (!nonceCache.checkAndStoreNonce(nonce)) {
                    log.warn("Internal S2S nonce replay detected: serviceId={}, nonce={}", serviceId, nonce);
                    filterChain.doFilter(request, response);
                    return;
                }

                if (!authorizedServices.contains(serviceId)) {
                    log.warn("Unauthorized internal service ID: {}", serviceId);
                    filterChain.doFilter(request, response);
                    return;
                }

                String method = request.getMethod();
                String path = request.getRequestURI();
                String bodyHash = signatureUtil.calculateBodyHash(new byte[0]);
                String canonicalString = signatureUtil.buildCanonicalString(method, path, timestamp, nonce, bodyHash);

                if (signatureUtil.verifySignature(canonicalString, internalSecret, signature)) {
                    UserPrincipal principal = UserPrincipal.create(serviceId, serviceId + "@internal.rrv", "ROLE_INTERNAL_SERVICE", null, "ACTIVE");
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    log.debug("Authenticated internal service: {}", serviceId);
                } else {
                    log.warn("Internal S2S signature verification failed for service: {}", serviceId);
                }

            } catch (Exception ex) {
                log.error("Error during internal S2S authentication: {}", ex.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}
