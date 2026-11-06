package com.rrvgreenbasket.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * RequestTracingFilter
 *
 * <p><strong>What it does:</strong></p>
 * Edge reactive {@link WebFilter} ensuring that every incoming HTTP request receives
 * or preserves a unique distributed tracing identifier ({@code X-Correlation-Id}).
 *
 * <p><strong>Why it is required:</strong></p>
 * In an enterprise ecosystem of 17 services, debugging an issue (e.g. checkout failure
 * or payment drop) across multiple asynchronous log files is impossible without a
 * unified correlation ID that ties the client request to all downstream service logs.
 *
 * <p><strong>How it works:</strong></p>
 * Implemented as a Spring WebFlux {@link WebFilter} with {@code HIGHEST_PRECEDENCE}
 * so that it intercepts every inbound request (both Gateway routed requests and local
 * management endpoints). Injects {@code X-Correlation-Id} downstream and registers
 * a {@code beforeCommit} hook to ensure the header is echoed back to the client.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTracingFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestTracingFilter.class);
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);

        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = UUID.randomUUID().toString();
            log.debug("Generated new Correlation-ID: {} for path: {}", correlationId, request.getPath());
        } else {
            log.debug("Preserved inbound Correlation-ID: {} for path: {}", correlationId, request.getPath());
        }

        // Mutate downstream request with Correlation-ID
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(CORRELATION_ID_HEADER, correlationId)
                .build();

        // Echo Correlation-ID in the response headers using beforeCommit
        final String finalCorrelationId = correlationId;
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, finalCorrelationId);
            return Mono.empty();
        });

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }
}
