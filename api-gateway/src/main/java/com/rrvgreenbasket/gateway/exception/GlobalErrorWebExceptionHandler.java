package com.rrvgreenbasket.gateway.exception;

import com.rrvgreenbasket.gateway.dto.response.ApiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * GlobalErrorWebExceptionHandler
 *
 * <p><strong>What it does:</strong></p>
 * Reactive exception handler intercepting all uncaught errors within the Spring Cloud Gateway
 * pipeline (e.g., 503 Service Unavailable when a downstream microservice is offline, 404 route not found).
 *
 * <p><strong>Why it is required:</strong></p>
 * Ensures that even when a downstream service is down, the Next.js frontend receives a clean,
 * standard JSON {@link ApiResponse} instead of raw HTML error pages or empty connection drops.
 *
 * <p><strong>How it works:</strong></p>
 * Ordered at {@code -2} to take precedence over the default Spring WebFlux error handler.
 * Serializes standard JSON envelopes with status code, failure message, and request path.
 */
@Component
@Order(-2)
public class GlobalErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalErrorWebExceptionHandler.class);
    private final ObjectMapper objectMapper;

    public GlobalErrorWebExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String errorMessage = "An unexpected error occurred in the API Gateway";

        if (ex instanceof ResponseStatusException responseStatusException) {
            status = HttpStatus.valueOf(responseStatusException.getStatusCode().value());
            errorMessage = responseStatusException.getReason() != null ? responseStatusException.getReason() : ex.getMessage();
        } else if (ex.getMessage() != null && (ex.getMessage().contains("Connection refused") || ex.getMessage().contains("503"))) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            errorMessage = "The requested backend microservice is currently unavailable. Please verify service registration.";
        }

        log.error("API Gateway error on path '{}' [Status: {}]: {}", exchange.getRequest().getPath(), status, ex.getMessage());

        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> errorDetails = new HashMap<>();
        errorDetails.put("path", exchange.getRequest().getPath().value());
        errorDetails.put("error", ex.getClass().getSimpleName());

        ApiResponse<Map<String, String>> apiResponse = ApiResponse.error(errorMessage, errorDetails);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(apiResponse);
        } catch (JsonProcessingException e) {
            bytes = ("{\"success\":false,\"message\":\"" + errorMessage + "\"}").getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
