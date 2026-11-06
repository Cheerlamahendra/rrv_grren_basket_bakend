package com.rrvgreenbasket.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.gateway.dto.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * PerimeterSecurityFilter
 *
 * <p><strong>What it does:</strong></p>
 * Edge perimeter filter executing at HIGHEST_PRECEDENCE.
 * <ul>
 *   <li>1. Strips all client-supplied <code>X-User-*</code>, <code>X-Session-*</code>,
 *       <code>X-Internal-*</code>, and <code>X-Gateway-*</code> headers to prevent spoofing.</li>
 *   <li>2. Blocks any external requests targeting internal microservice endpoints (<code>/internal/**</code>).</li>
 * </ul>
 */
@Component
public class PerimeterSecurityFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(PerimeterSecurityFilter.class);
    private final ObjectMapper objectMapper;

    public PerimeterSecurityFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // 1. Block external access to any /internal/** routes
        if (path.contains("/internal/") || path.endsWith("/internal")) {
            log.warn("Blocked external request targeting internal path: {}", path);
            return onForbidden(exchange, "Access denied: Internal microservice endpoints cannot be accessed externally");
        }

        // 2. Strip any inbound spoofed identity, session, or internal headers
        List<String> headersToRemove = new ArrayList<>();
        request.getHeaders().keySet().forEach(headerName -> {
            String lower = headerName.toLowerCase();
            if (lower.startsWith("x-user-") || lower.startsWith("x-session-")
                    || lower.startsWith("x-internal-") || lower.startsWith("x-gateway-")) {
                headersToRemove.add(headerName);
            }
        });

        ServerHttpRequest.Builder requestBuilder = request.mutate();
        if (!headersToRemove.isEmpty()) {
            log.debug("Stripping client-supplied headers: {}", headersToRemove);
            requestBuilder.headers(httpHeaders -> headersToRemove.forEach(httpHeaders::remove));
        }

        return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
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

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE; // Runs before all filters
    }
}
