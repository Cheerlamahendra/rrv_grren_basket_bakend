package com.rrvgreenbasket.product.security;

import com.rrvgreenbasket.product.client.AuthServiceClient;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final AuthServiceClient authServiceClient;
    private final NonceReplayCache nonceReplayCache;
    private final SecretKey internalKey;

    public HeaderAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            AuthServiceClient authServiceClient,
            NonceReplayCache nonceReplayCache,
            @Value("${app.security.internal-service-secret:c8f7e2d1a5b49382716054321fedcba9876543210abcdef0123456789abcdef0}") String internalSecret) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.authServiceClient = authServiceClient;
        this.nonceReplayCache = nonceReplayCache;

        byte[] keyBytes = internalSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        this.internalKey = Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        try {
            String gatewayAssertion = request.getHeader("X-Gateway-Assertion");

            // Option 1: Gateway Cryptographic Assertion JWT
            if (StringUtils.hasText(gatewayAssertion)) {
                Claims claims = Jwts.parser()
                        .verifyWith(internalKey)
                        .build()
                        .parseSignedClaims(gatewayAssertion.trim())
                        .getPayload();

                String nonce = claims.getId();
                if (nonce != null && !nonceReplayCache.checkAndStoreNonce(nonce)) {
                    log.warn("Gateway assertion replay detected for nonce '{}'", nonce);
                    filterChain.doFilter(request, response);
                    return;
                }

                String userId = claims.getSubject();
                String role = claims.get("role", String.class);
                String sessionId = claims.get("sid", String.class);
                String email = request.getHeader("X-User-Email");

                UserPrincipal principal = UserPrincipal.create(userId, email, role, sessionId, "ACTIVE");
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
                log.debug("Authenticated via Gateway Assertion: userId={}, role={}", userId, role);

            } else {
                // Option 2: Direct Bearer JWT (Strictly ignores unverified client headers)
                String bearerToken = getJwtFromRequest(request);
                if (StringUtils.hasText(bearerToken) && jwtTokenProvider.validateToken(bearerToken)) {
                    String extractedUserId = jwtTokenProvider.extractUserId(bearerToken);
                    String extractedEmail = jwtTokenProvider.extractEmail(bearerToken);
                    String extractedRole = jwtTokenProvider.extractRole(bearerToken);
                    String sessionId = jwtTokenProvider.extractSessionId(bearerToken);

                    boolean sessionActive = (sessionId == null || sessionId.isBlank())
                            || authServiceClient.isSessionActive(sessionId);

                    if (sessionActive) {
                        UserPrincipal principal = UserPrincipal.create(extractedUserId, extractedEmail, extractedRole, sessionId, "ACTIVE");
                        UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                        log.debug("Authenticated via direct Bearer JWT: userId={}, role={}", extractedUserId, extractedRole);
                    } else {
                        log.warn("Direct Bearer token rejected: session '{}' is revoked or invalid", sessionId);
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context: {}", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
