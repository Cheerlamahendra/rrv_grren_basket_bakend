package com.rrvgreenbasket.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.gateway.dto.response.ApiResponse;
import com.rrvgreenbasket.gateway.util.GatewayAssertionUtil;
import com.rrvgreenbasket.gateway.util.JwtUtil;
import com.rrvgreenbasket.gateway.util.S2SSignatureUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JwtAuthenticationFilter
 *
 * <p><strong>What it does:</strong></p>
 * Reactive edge security filter intercepting inbound requests, verifying JWT access tokens,
 * executing real-time session validation with Auth Service, maintaining a revoked-session set,
 * and generating short-lived <code>X-Gateway-Assertion</code> JWTs downstream.
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtil jwtUtil;
    private final GatewayAssertionUtil assertionUtil;
    private final S2SSignatureUtil signatureUtil;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // High-concurrency negative cache for revoked session IDs (guarantees immediate edge invalidation)
    private final Set<String> revokedSessionSet = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final String authServiceBaseUrl;
    private final String internalSecret;
    private final String serviceId;

    private static final List<String> DEFAULT_PUBLIC_ENDPOINTS = List.of(
            "/api/auth/customer/register/**",
            "/api/auth/customer/login/**",
            "/api/auth/customer/send-otp",
            "/api/auth/customer/verify-otp",
            "/api/auth/customer/register",
            "/api/auth/customer/login",
            "/api/auth/admin/login",
            "/api/auth/refresh",
            "/api/auth/customer/refresh",
            "/api/gateway/status",
            "/api/gateway/routes",
            "/fallback/**",
            "/actuator/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/webjars/**"
    );

    @Value("${app.jwt.public-endpoints:}")
    private List<String> publicEndpoints;

    public JwtAuthenticationFilter(
            JwtUtil jwtUtil,
            GatewayAssertionUtil assertionUtil,
            S2SSignatureUtil signatureUtil,
            ObjectMapper objectMapper,
            WebClient.Builder webClientBuilder,
            @Value("${app.services.auth-service-url:http://localhost:8081}") String authServiceBaseUrl,
            @Value("${app.security.internal-service-secret:c8f7e2d1a5b49382716054321fedcba9876543210abcdef0123456789abcdef0}") String internalSecret,
            @Value("${app.security.internal-service-id:api-gateway}") String serviceId) {

        this.jwtUtil = jwtUtil;
        this.assertionUtil = assertionUtil;
        this.signatureUtil = signatureUtil;
        this.objectMapper = objectMapper;
        this.authServiceBaseUrl = authServiceBaseUrl.replaceAll("/+$", "");
        this.internalSecret = internalSecret;
        this.serviceId = serviceId;
        this.webClient = webClientBuilder.baseUrl(this.authServiceBaseUrl).build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        HttpMethod method = request.getMethod();

        // 1. Bypass CORS preflight requests
        if (HttpMethod.OPTIONS.equals(method)) {
            return chain.filter(exchange);
        }

        // 2. Check if route is public (logout is NEVER public)
        if (isPublicEndpoint(path, method)) {
            return chain.filter(exchange);
        }

        // 3. Extract Authorization Header
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing Authorization Bearer header for protected path: {}", path);
            return onError(exchange, "Missing or invalid Authorization header. Expected Bearer token.", HttpStatus.UNAUTHORIZED);
        }

        String token = authHeader.substring(7).trim();

        // 4. Validate JWT Signature & Expiration
        if (!jwtUtil.validateToken(token)) {
            log.warn("Invalid or expired JWT token for path '{}'", path);
            return onError(exchange, "Invalid or expired JWT token.", HttpStatus.UNAUTHORIZED);
        }

        // 5. Extract Identity & Session Claims
        String userId = jwtUtil.extractUserId(token);
        String role = jwtUtil.extractRole(token);
        String sessionId = jwtUtil.extractSessionId(token);
        String email = jwtUtil.extractEmail(token);
        String mobile = jwtUtil.extractMobileNumber(token);

        // 6. Real-time Session Revocation Check
        if (sessionId != null && !sessionId.isBlank()) {
            if (revokedSessionSet.contains(sessionId)) {
                log.warn("Immediate edge rejection: Session '{}' is in local revoked session set", sessionId);
                return onError(exchange, "Session has been revoked or invalidated.", HttpStatus.UNAUTHORIZED);
            }

            return validateSessionWithAuthService(sessionId)
                    .flatMap(isValid -> {
                        if (!isValid) {
                            revokedSessionSet.add(sessionId);
                            log.warn("Session '{}' reported INVALID/REVOKED by Auth Service", sessionId);
                            return onError(exchange, "Session has been revoked or invalidated.", HttpStatus.UNAUTHORIZED);
                        }

                        // Generate Short-Lived Gateway Assertion JWT
                        String assertionJwt = assertionUtil.generateAssertion(
                                userId,
                                role,
                                sessionId,
                                method != null ? method.name() : "GET",
                                path,
                                ""
                        );

                        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                                .header("X-Gateway-Assertion", assertionJwt)
                                .header("X-User-Id", userId != null ? userId : "")
                                .header("X-User-Email", email != null ? email : (mobile != null ? mobile : ""))
                                .header("X-User-Role", role != null ? role : "")
                                .header("X-Session-Id", sessionId)
                                .build();

                        return chain.filter(exchange.mutate().request(mutatedRequest).build())
                                .then(Mono.fromRunnable(() -> {
                                    // If this was a successful logout request, immediately record session as revoked
                                    if (path.contains("/logout") && exchange.getResponse().getStatusCode() != null
                                            && exchange.getResponse().getStatusCode().is2xxSuccessful()) {
                                        revokedSessionSet.add(sessionId);
                                        log.info("Immediate invalidation: Added session '{}' to revokedSessionSet upon logout", sessionId);
                                    }
                                }));
                    });
        }

        // For tokens without sid (legacy), proceed with generated assertion
        String assertionJwt = assertionUtil.generateAssertion(userId, role, "", method != null ? method.name() : "GET", path, "");
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header("X-Gateway-Assertion", assertionJwt)
                .header("X-User-Id", userId != null ? userId : "")
                .header("X-User-Email", email != null ? email : "")
                .header("X-User-Role", role != null ? role : "")
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private Mono<Boolean> validateSessionWithAuthService(String sessionId) {
        String path = "/api/auth/internal/sessions/" + sessionId + "/validate";
        long timestamp = System.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();
        String bodyHash = signatureUtil.calculateBodyHash(new byte[0]);
        String canonicalString = signatureUtil.buildCanonicalString("GET", path, timestamp, nonce, bodyHash);
        String signature = signatureUtil.calculateSignature(canonicalString, internalSecret);

        return webClient.get()
                .uri(path)
                .header("X-Internal-Service-Id", serviceId)
                .header("X-Internal-Key-Id", "v1")
                .header("X-Internal-Timestamp", String.valueOf(timestamp))
                .header("X-Internal-Nonce", nonce)
                .header("X-Internal-Signature", signature)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(3000))
                .map(json -> {
                    try {
                        JsonNode root = objectMapper.readTree(json);
                        return root.path("valid").asBoolean(false);
                    } catch (Exception e) {
                        log.error("Failed to parse session validation JSON: {}", e.getMessage());
                        return false;
                    }
                })
                .onErrorResume(ex -> {
                    log.error("Auth Service session validation call failed: {}", ex.getMessage());
                    return Mono.just(false);
                });
    }

    private boolean isPublicEndpoint(String path, HttpMethod method) {
        // Logout is NEVER public
        if (path.contains("/logout")) {
            return false;
        }

        // Read-only catalog endpoints are public
        if (HttpMethod.GET.equals(method) && (path.startsWith("/api/products") || path.startsWith("/api/reviews/products"))) {
            return true;
        }

        // Bulk rating summary is public for product cards
        if (HttpMethod.POST.equals(method) && path.startsWith("/api/reviews/products/summaries")) {
            return true;
        }

        if (publicEndpoints != null && !publicEndpoints.isEmpty()) {
            for (String pattern : publicEndpoints) {
                if (pattern != null && !pattern.isBlank() && pathMatcher.match(pattern.trim(), path)) {
                    return true;
                }
            }
        }

        for (String pattern : DEFAULT_PUBLIC_ENDPOINTS) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private Mono<Void> onError(ServerWebExchange exchange, String errorMessage, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiResponse<Void> apiResponse = ApiResponse.error(errorMessage);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(apiResponse);
        } catch (JsonProcessingException e) {
            bytes = ("{\"success\":false,\"message\":\"" + errorMessage + "\"}").getBytes(StandardCharsets.UTF_8);
        }

        response.getHeaders().setContentLength(bytes.length);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10; // Runs right after PerimeterSecurityFilter
    }
}
