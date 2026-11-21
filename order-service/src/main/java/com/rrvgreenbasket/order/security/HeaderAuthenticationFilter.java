package com.rrvgreenbasket.order.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.order.dto.response.ApiResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

@Slf4j
@Component
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    private static final String ASSERTION_HEADER = "X-Gateway-Assertion";

    private final SecretKey signingKey;
    private final String issuer;
    private final String audience;
    private final ReplayProtectionService replayProtectionService;
    private final ObjectMapper objectMapper;

    @Autowired
    public HeaderAuthenticationFilter(
            ReplayProtectionService replayProtectionService,
            ObjectMapper objectMapper,
            @Value("${app.security.internal-service-secret:RQpmB5hGO2NsLNE7OnwXo5NfNtEJj+j+461g0oEeGunJPZ4iYQEGr+uk5M3hAfgDg26IDtmA9N2RJbFXkCLrqQ==}") String secret,
            @Value("${app.jwt.issuer:rrv-gateway}") String issuer,
            @Value("${app.security.gateway-audience:rrv-internal-services}") String audience) {

        this.replayProtectionService = replayProtectionService;
        this.objectMapper = objectMapper;

        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException(
                    "Gateway assertion secret property 'app.security.internal-service-secret' is missing or blank. A 512-bit Base64-encoded secret is required for HS512."
            );
        }

        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret.trim());
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Gateway assertion secret property 'app.security.internal-service-secret' is not valid Base64: " + e.getMessage(), e
            );
        }

        if (keyBytes.length < 64) {
            throw new IllegalStateException(
                    "Gateway assertion secret must be at least 512 bits (64 bytes) for HS512. Decoded key size is " + (keyBytes.length * 8) + " bits."
            );
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

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String assertion = request.getHeader(ASSERTION_HEADER);

        if (!StringUtils.hasText(assertion)) {
            SecurityContextHolder.clearContext();
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(assertion.trim())
                    .getPayload();

            String userId = claims.getSubject();
            String role = claims.get("role", String.class);
            String sessionId = claims.get("sid", String.class);
            String assertionId = claims.getId();

            String assertedMethod = claims.get("m", String.class);
            String assertedPath = claims.get("p", String.class);

            if (!StringUtils.hasText(userId) || !StringUtils.hasText(role)) {
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Required assertion claims are missing");
                return;
            }

            // Method & path binding verification
            if (assertedMethod != null && !request.getMethod().equalsIgnoreCase(assertedMethod)) {
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Assertion request method mismatch");
                return;
            }

            if (assertedPath != null && !request.getRequestURI().equals(assertedPath)) {
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Assertion request path mismatch");
                return;
            }

            // Expiration validation
            Date expiration = claims.getExpiration();
            long now = System.currentTimeMillis();
            if (expiration == null || expiration.getTime() <= now) {
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Assertion has expired");
                return;
            }

            // Replay protection if jti present
            if (StringUtils.hasText(assertionId)) {
                long ttlMs = expiration.getTime() - now;
                if (!replayProtectionService.checkAndStoreNonce(assertionId, ttlMs > 0 ? ttlMs : 30_000L)) {
                    sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Replayed gateway assertion");
                    return;
                }
            }

            // Role enforcement by path
            String uri = request.getRequestURI();
            if (uri.startsWith("/api/admin/")) {
                if (!"ADMIN".equalsIgnoreCase(role) && !"ROLE_ADMIN".equalsIgnoreCase(role)
                        && !"SUPER_ADMIN".equalsIgnoreCase(role) && !"ROLE_SUPER_ADMIN".equalsIgnoreCase(role)) {
                    sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, "Admin privileges required");
                    return;
                }
            } else if (uri.startsWith("/api/internal/")) {
                if (!"INTERNAL_SERVICE".equalsIgnoreCase(role) && !"ROLE_INTERNAL_SERVICE".equalsIgnoreCase(role)
                        && !"ADMIN".equalsIgnoreCase(role) && !"ROLE_ADMIN".equalsIgnoreCase(role)) {
                    sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, "Internal service access required");
                    return;
                }
            } else if (uri.startsWith("/api/orders")) {
                if (!"CUSTOMER".equalsIgnoreCase(role) && !"ROLE_CUSTOMER".equalsIgnoreCase(role)) {
                    sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, "Customer access required");
                    return;
                }
            }

            UserPrincipal principal = new UserPrincipal(userId, role, sessionId);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Authenticated gateway assertion for user: {} with role: {}", userId, role);

            filterChain.doFilter(request, response);

        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("Gateway assertion verification failed: {}", ex.getMessage());
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid gateway assertion");
        }
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
