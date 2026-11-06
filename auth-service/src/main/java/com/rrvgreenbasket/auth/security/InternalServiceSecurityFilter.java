package com.rrvgreenbasket.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.auth.dto.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * InternalServiceSecurityFilter
 *
 * <p><strong>What it does:</strong></p>
 * Secures all internal microservice endpoints under {@code /api/auth/internal/**}.
 * Enforces canonical request signing (HMAC-SHA256), sliding nonce replay protection,
 * timestamp freshness (max 30s skew), and versioned secret verification.
 */
@Component
public class InternalServiceSecurityFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalServiceSecurityFilter.class);
    private static final long MAX_SKEW_MS = 30_000L; // 30 seconds maximum clock skew

    private final S2SSignatureUtil signatureUtil;
    private final NonceReplayCache nonceReplayCache;
    private final ObjectMapper objectMapper;
    private final String internalSecret;
    private final List<String> authorizedServices;

    public InternalServiceSecurityFilter(
            S2SSignatureUtil signatureUtil,
            NonceReplayCache nonceReplayCache,
            ObjectMapper objectMapper,
            @Value("${app.security.internal-service-secret:c8f7e2d1a5b49382716054321fedcba9876543210abcdef0123456789abcdef0}") String internalSecret,
            @Value("${app.security.authorized-internal-services:api-gateway,customer-service,order-service}") List<String> authorizedServices) {
        this.signatureUtil = signatureUtil;
        this.nonceReplayCache = nonceReplayCache;
        this.objectMapper = objectMapper;
        this.internalSecret = internalSecret;
        this.authorizedServices = authorizedServices;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/auth/internal/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);

        String serviceId = wrappedRequest.getHeader("X-Internal-Service-Id");
        String keyId = wrappedRequest.getHeader("X-Internal-Key-Id");
        String timestampHeader = wrappedRequest.getHeader("X-Internal-Timestamp");
        String nonce = wrappedRequest.getHeader("X-Internal-Nonce");
        String signature = wrappedRequest.getHeader("X-Internal-Signature");

        // 1. Validate presence of all mandatory S2S security headers
        if (serviceId == null || timestampHeader == null || nonce == null || signature == null) {
            log.warn("Missing mandatory S2S headers for internal endpoint: path={}, serviceId={}",
                    wrappedRequest.getRequestURI(), serviceId);
            sendErrorResponse(response, HttpStatus.FORBIDDEN, "Access denied: Missing internal service authentication headers");
            return;
        }

        // 2. Validate authorized calling service identity
        if (!authorizedServices.contains(serviceId.trim().toLowerCase())) {
            log.warn("Unauthorized calling service '{}' attempted to access internal endpoint: {}",
                    serviceId, wrappedRequest.getRequestURI());
            sendErrorResponse(response, HttpStatus.FORBIDDEN, "Access denied: Unauthorized service caller: " + serviceId);
            return;
        }

        // 3. Validate timestamp freshness (max 30s skew to prevent replay)
        long requestTime;
        try {
            requestTime = Long.parseLong(timestampHeader.trim());
        } catch (NumberFormatException e) {
            sendErrorResponse(response, HttpStatus.FORBIDDEN, "Access denied: Malformed timestamp header");
            return;
        }

        long now = System.currentTimeMillis();
        if (Math.abs(now - requestTime) > MAX_SKEW_MS) {
            log.warn("S2S request rejected: timestamp skew exceeded limit (skew={}ms, max={}ms)",
                    Math.abs(now - requestTime), MAX_SKEW_MS);
            sendErrorResponse(response, HttpStatus.FORBIDDEN, "Access denied: Request timestamp expired or invalid clock skew");
            return;
        }

        // 4. Nonce Replay Prevention Check
        if (!nonceReplayCache.checkAndStoreNonce(nonce.trim())) {
            log.warn("S2S replay attack detected for nonce '{}' from service '{}'", nonce, serviceId);
            sendErrorResponse(response, HttpStatus.FORBIDDEN, "Access denied: Replay attack detected. Nonce already consumed.");
            return;
        }

        // 5. Construct canonical request string and verify HMAC signature
        byte[] bodyBytes = wrappedRequest.getInputStream().readAllBytes();
        String bodyHash = signatureUtil.calculateBodyHash(bodyBytes);

        String pathAndQuery = wrappedRequest.getRequestURI();
        if (wrappedRequest.getQueryString() != null) {
            pathAndQuery += "?" + wrappedRequest.getQueryString();
        }

        String canonicalString = signatureUtil.buildCanonicalString(
                wrappedRequest.getMethod(),
                pathAndQuery,
                requestTime,
                nonce.trim(),
                bodyHash
        );

        if (!signatureUtil.verifySignature(canonicalString, internalSecret, signature.trim())) {
            log.warn("S2S signature mismatch for service '{}', path '{}'", serviceId, wrappedRequest.getRequestURI());
            sendErrorResponse(response, HttpStatus.FORBIDDEN, "Access denied: Invalid S2S cryptographic signature");
            return;
        }

        // 6. Signature verified! Establish internal service security context
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                serviceId,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_INTERNAL_SERVICE"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.debug("S2S request authenticated successfully for service '{}', path '{}'",
                serviceId, wrappedRequest.getRequestURI());

        filterChain.doFilter(wrappedRequest, response);
    }

    private void sendErrorResponse(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<Void> apiResponse = ApiResponse.error(message);
        byte[] jsonBytes = objectMapper.writeValueAsString(apiResponse).getBytes(StandardCharsets.UTF_8);
        response.getOutputStream().write(jsonBytes);
    }
}
