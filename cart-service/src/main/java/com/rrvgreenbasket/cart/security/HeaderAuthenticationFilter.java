
    package com.rrvgreenbasket.cart.security;

    import io.jsonwebtoken.Claims;
    import io.jsonwebtoken.JwtException;
    import io.jsonwebtoken.Jwts;
    import io.jsonwebtoken.security.Keys;
    import jakarta.servlet.FilterChain;
    import jakarta.servlet.ServletException;
    import jakarta.servlet.http.HttpServletRequest;
    import jakarta.servlet.http.HttpServletResponse;
    import org.springframework.beans.factory.annotation.Value;
    import org.springframework.http.HttpHeaders;
    import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
    import org.springframework.security.core.context.SecurityContextHolder;
    import org.springframework.stereotype.Component;
    import org.springframework.web.filter.OncePerRequestFilter;

    import javax.crypto.SecretKey;
    import java.io.IOException;
    import java.nio.charset.StandardCharsets;
    import java.util.Date;
    import java.util.Map;
    import java.util.concurrent.ConcurrentHashMap;

    @Component
    public class HeaderAuthenticationFilter extends OncePerRequestFilter {

        private static final String ASSERTION_HEADER = "X-Gateway-Assertion";

        private final SecretKey signingKey;
        private final String issuer;
        private final String audience;

        // Stores assertion IDs until their expiration time.
        private final Map<String, Long> usedAssertions = new ConcurrentHashMap<>();

        public HeaderAuthenticationFilter(
                @Value("${app.security.internal-service-secret}") String secret,
                @Value("${app.jwt.issuer:rrv-gateway}") String issuer,
                @Value("${app.security.gateway-audience:rrv-internal-services}") String audience) {

            byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);

            if (keyBytes.length < 32) {
                throw new IllegalArgumentException(
                        "Gateway assertion secret must contain at least 32 bytes"
                );
            }

            this.signingKey = Keys.hmacShaKeyFor(keyBytes);
            this.issuer = issuer;
            this.audience = audience;
        }

        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain) throws ServletException, IOException {

            String assertion = request.getHeader(ASSERTION_HEADER);

            if (assertion == null || assertion.isBlank()) {
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }

            try {
                Claims claims = Jwts.parser()
                        .verifyWith(signingKey)
                        .requireIssuer(issuer)
                        .requireAudience(audience)
                        .build()
                        .parseSignedClaims(assertion)
                        .getPayload();

                String userId = claims.getSubject();
                String role = claims.get("role", String.class);
                String sessionId = claims.get("sid", String.class);
                String assertionId = claims.getId();

                String assertedMethod = claims.get("m", String.class);
                String assertedPath = claims.get("p", String.class);

                if (userId == null || userId.isBlank()
                        || role == null || role.isBlank()
                        || assertionId == null || assertionId.isBlank()) {
                    throw new JwtException("Required assertion claims are missing");
                }

                // Ensure assertion belongs to this exact request.
                if (!request.getMethod().equalsIgnoreCase(assertedMethod)
                        || !request.getRequestURI().equals(assertedPath)) {
                    throw new JwtException("Assertion request binding mismatch");
                }

                // Cart customer endpoints require CUSTOMER role.
                if (!"CUSTOMER".equalsIgnoreCase(role)
                        && !"ROLE_CUSTOMER".equalsIgnoreCase(role)) {
                    response.sendError(
                            HttpServletResponse.SC_FORBIDDEN,
                            "Customer access required"
                    );
                    return;
                }

                long now = System.currentTimeMillis();
                Date expiration = claims.getExpiration();

                if (expiration == null || expiration.getTime() <= now) {
                    throw new JwtException("Assertion has expired");
                }

                // Remove expired assertion IDs.
                usedAssertions.entrySet().removeIf(
                        entry -> entry.getValue() <= now
                );

                // Reject reuse of the same assertion.
                Long previous = usedAssertions.putIfAbsent(
                        assertionId,
                        expiration.getTime()
                );

                if (previous != null) {
                    throw new JwtException("Replayed gateway assertion");
                }

                UserPrincipal principal =
                        new UserPrincipal(userId, role, sessionId);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                principal.getAuthorities()
                        );

                SecurityContextHolder.getContext()
                        .setAuthentication(authentication);

                filterChain.doFilter(request, response);

            } catch (JwtException | IllegalArgumentException ex) {
                SecurityContextHolder.clearContext();

                response.sendError(
                        HttpServletResponse.SC_UNAUTHORIZED,
                        "Invalid gateway assertion"
                );
            }
            filterChain.doFilter(request, response);
        }
    }
