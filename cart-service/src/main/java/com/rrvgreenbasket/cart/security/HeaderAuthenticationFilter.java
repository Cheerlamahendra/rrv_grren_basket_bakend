package com.rrvgreenbasket.cart.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.cart.client.AuthServiceClient;
import com.rrvgreenbasket.cart.dto.response.ApiResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
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
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HeaderAuthenticationFilter
 *
 * <p><strong>What it does:</strong></p>
 * Defense-in-depth security filter verifying cryptographically signed X-Gateway-Assertion JWTs
 * from API Gateway, enforcing nonce replay protection, request method/path binding, role checks,
 * and authoritative session revocation with Auth Service.
 */
@Slf4j
@Component
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    private static final String ASSERTION_HEADER = "X-Gateway-Assertion";

    private final SecretKey signingKey;
    private final String issuer;
    private final String audience;
    private final ReplayProtectionService replayProtectionService;
    private final AuthServiceClient authServiceClient;
    private final ObjectMapper objectMapper;

    // Fallback/direct thread-safe replay protection cache: stores assertion IDs (jti) until expiration
    private final Map<String, Long> usedAssertions = new ConcurrentHashMap<>();

    public HeaderAuthenticationFilter(
            ReplayProtectionService replayProtectionService,
            AuthServiceClient authServiceClient,
            ObjectMapper objectMapper,
            @Value("${app.security.internal-service-secret:c8f7e2d1a5b49382716054321fedcba9876543210abcdef0123456789abcdef0}") String secret,
            @Value("${app.jwt.issuer:rrv-gateway}") String issuer,
            @Value("${app.security.gateway-audience:rrv-internal-services}") String audience) {

        this.replayProtectionService = replayProtectionService;
        this.authServiceClient = authServiceClient;
        this.objectMapper = objectMapper;

        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }

        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.issuer = issuer;
        this.audience = audience;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        log.debug("[CartAuth] Request received: {} {}", request.getMethod(), request.getRequestURI());

        // If request is already authenticated, continue
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String assertion = request.getHeader(ASSERTION_HEADER);

        // If gateway assertion is missing, allow unauthenticated request to proceed to Spring Security
        // (Spring Security will permit public endpoints like /actuator/** and reject secured /api/cart/** with 401)
        if (!StringUtils.hasText(assertion)) {
            log.debug("[CartAuth] Gateway assertion present: false");
            SecurityContextHolder.clearContext();
            filterChain.doFilter(request, response);
            return;
        }

        log.debug("[CartAuth] Gateway assertion present: true");

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .requireAudience(audience)
                    .build()
                    .parseSignedClaims(assertion.trim())
                    .getPayload();

            log.debug("[CartAuth] Assertion signature valid: true");
            log.debug("[CartAuth] Issuer valid: true");
            log.debug("[CartAuth] Audience valid: true");

            String userId = claims.getSubject();
            String role = claims.get("role", String.class);
            String sessionId = claims.get("sid", String.class);
            String assertionId = claims.getId();

            String assertedMethod = claims.get("m", String.class);
            String assertedPath = claims.get("p", String.class);

            if (!StringUtils.hasText(userId) || !StringUtils.hasText(role) || !StringUtils.hasText(assertionId)) {
                log.warn("[CartAuth] Authentication failed: MISSING_CLAIMS");
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Required assertion claims are missing");
                return;
            }

            // Ensure assertion is strictly bound to this exact request method
            if (assertedMethod != null && !isMethodMatching(assertedMethod, request.getMethod())) {
                log.warn("[CartAuth] Authentication failed: METHOD_MISMATCH (asserted={}, actual={})",
                        assertedMethod, request.getMethod());
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Assertion request method mismatch");
                return;
            }

            // Ensure assertion is strictly bound to this exact request URI
            if (assertedPath != null && !isPathMatching(assertedPath, request.getRequestURI())) {
                log.warn("[CartAuth] Authentication failed: PATH_MISMATCH (asserted={}, actual={})",
                        assertedPath, request.getRequestURI());
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Assertion request path mismatch");
                return;
            }

            // Cart customer endpoints require CUSTOMER or USER role (or internal services)
            boolean isAllowedRole = "CUSTOMER".equalsIgnoreCase(role)
                    || "ROLE_CUSTOMER".equalsIgnoreCase(role)
                    || "USER".equalsIgnoreCase(role)
                    || "ROLE_USER".equalsIgnoreCase(role)
                    || "INTERNAL_SERVICE".equalsIgnoreCase(role)
                    || "ROLE_INTERNAL_SERVICE".equalsIgnoreCase(role);

            if (!isAllowedRole) {
                log.warn("[CartAuth] Authentication failed: FORBIDDEN_ROLE (role={})", role);
                sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, "Customer access required");
                return;
            }

            // Validate expiration
            Date expiration = claims.getExpiration();
            long now = System.currentTimeMillis();
            if (expiration == null || expiration.getTime() <= now) {
                log.warn("[CartAuth] Authentication failed: EXPIRED_ASSERTION");
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Assertion has expired");
                return;
            }

            // Enforce Nonce Replay Protection
            long ttlMs = expiration.getTime() - now;
            long effectiveTtl = ttlMs > 0 ? ttlMs : 30_000L;

            boolean nonceValid;
            if (replayProtectionService != null) {
                nonceValid = replayProtectionService.checkAndStoreNonce(assertionId, effectiveTtl);
            } else {
                usedAssertions.entrySet().removeIf(entry -> entry.getValue() <= now);
                Long previous = usedAssertions.putIfAbsent(assertionId, expiration.getTime());
                nonceValid = (previous == null || previous <= now);
            }

            if (!nonceValid) {
                log.warn("[CartAuth] Authentication failed: REPLAY_DETECTED");
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Replayed gateway assertion");
                return;
            }

            // Authoritative session revocation validation with Auth Service (if configured and present)
            // Note: Synthetic internal/saga sessions (e.g. order-saga-*, s2s-*, internal-*) are generated
            // by internal services for S2S calls and do not exist in the customer session database.
            boolean isSyntheticS2sSession = StringUtils.hasText(sessionId) && (
                    sessionId.startsWith("order-saga-") ||
                    sessionId.startsWith("s2s-") ||
                    sessionId.startsWith("internal-")
            );

            if (StringUtils.hasText(sessionId) && !isSyntheticS2sSession && authServiceClient != null) {
                if (!authServiceClient.isSessionActive(sessionId)) {
                    log.warn("[CartAuth] Authentication failed: REVOKED_SESSION for sessionId='{}'", sessionId);
                    sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Customer session has been revoked");
                    return;
                }
            }

            UserPrincipal principal = new UserPrincipal(userId, role, sessionId);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("[CartAuth] Customer identity validated: true");
            log.info("[CartAuth] Authentication successful for customer: {}", userId);

            // Successfully authenticated request proceeds exactly once
            filterChain.doFilter(request, response);

        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("[CartAuth] Authentication failed: INVALID_SIGNATURE - {}", ex.getMessage());
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid gateway assertion");
        }
    }

    private boolean isPathMatching(String assertedPath, String requestUri) {
        if (assertedPath == null || requestUri == null) {
            return false;
        }
        if (assertedPath.equals(requestUri)) {
            return true;
        }
        String normAsserted = assertedPath.endsWith("/") ? assertedPath.substring(0, assertedPath.length() - 1) : assertedPath;
        String normReq = requestUri.endsWith("/") ? requestUri.substring(0, requestUri.length() - 1) : requestUri;
        return normAsserted.equals(normReq);
    }

    private boolean isMethodMatching(String assertedMethod, String requestMethod) {
        if (assertedMethod == null || requestMethod == null) {
            return false;
        }
        return assertedMethod.equalsIgnoreCase(requestMethod);
    }

    private void sendErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiResponse<Void> errorEnvelope = ApiResponse.error(message);
        response.getWriter().write(objectMapper.writeValueAsString(errorEnvelope));
        response.getWriter().flush();
    }
}
