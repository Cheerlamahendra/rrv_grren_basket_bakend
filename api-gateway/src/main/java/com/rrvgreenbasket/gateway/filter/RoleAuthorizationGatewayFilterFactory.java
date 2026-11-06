package com.rrvgreenbasket.gateway.filter;

import com.rrvgreenbasket.gateway.dto.response.ApiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * RoleAuthorizationGatewayFilterFactory
 *
 * <p><strong>What it does:</strong></p>
 * Route-level Gateway filter that enforces Role-Based Access Control (RBAC).
 * Configured in {@code application.yml} on restricted route definitions (e.g. {@code /api/admin/**}).
 *
 * <p><strong>Why it is required:</strong></p>
 * Prevents non-administrative users (customers or delivery partners) from accessing administrative
 * management endpoints, even if they possess a valid customer JWT token.
 *
 * <p><strong>How it works:</strong></p>
 * Inspects the {@code X-User-Role} header populated by {@link JwtAuthenticationFilter}.
 * Matches against the configured {@code requiredRoles}. If mismatched, returns HTTP 403 Forbidden.
 */
@Component
public class RoleAuthorizationGatewayFilterFactory extends AbstractGatewayFilterFactory<RoleAuthorizationGatewayFilterFactory.Config> {

    private static final Logger log = LoggerFactory.getLogger(RoleAuthorizationGatewayFilterFactory.class);
    private final ObjectMapper objectMapper;

    public RoleAuthorizationGatewayFilterFactory(ObjectMapper objectMapper) {
        super(Config.class);
        this.objectMapper = objectMapper;
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return List.of("requiredRoles");
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String userRole = exchange.getRequest().getHeaders().getFirst("X-User-Role");
            log.debug("Checking RBAC authorization for userRole='{}', requiredRoles='{}'", userRole, config.getRequiredRoles());

            if (userRole == null || userRole.trim().isEmpty()) {
                return onForbidden(exchange, "Access denied: Missing role credentials");
            }

            List<String> allowedRoles = Arrays.stream(config.getRequiredRoles().split(","))
                    .map(String::trim)
                    .toList();

            boolean hasRole = allowedRoles.stream().anyMatch(role -> role.equalsIgnoreCase(userRole));
            if (!hasRole) {
                log.warn("Access denied for role '{}'. Required: {}", userRole, config.getRequiredRoles());
                return onForbidden(exchange, "Access denied: You do not possess the required administrative permissions");
            }

            return chain.filter(exchange);
        };
    }

    private Mono<Void> onForbidden(ServerWebExchange exchange, String errorMessage) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
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

    @Data
    public static class Config {
        private String requiredRoles;
    }
}
