package com.rrvgreenbasket.discovery.exception;

import com.rrvgreenbasket.discovery.dto.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * GlobalExceptionHandler
 *
 * <p><strong>What it does:</strong></p>
 * Centralized exception handling interceptor for the Discovery Server's REST endpoints.
 *
 * <p><strong>Why it is required:</strong></p>
 * Prevents raw stack traces or default Spring error pages from leaking to clients.
 * Wraps all failures inside standard {@link ApiResponse} envelopes with HTTP status codes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleResourceNotFoundException(
            ResourceNotFoundException ex, WebRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        Map<String, String> details = new HashMap<>();
        details.put("path", request.getDescription(false).replace("uri=", ""));
        return new ResponseEntity<>(ApiResponse.error(ex.getMessage(), details), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleGlobalException(
            Exception ex, WebRequest request) {
        log.error("Unhandled server exception: ", ex);
        Map<String, String> details = new HashMap<>();
        details.put("error", ex.getClass().getSimpleName());
        details.put("path", request.getDescription(false).replace("uri=", ""));
        return new ResponseEntity<>(
                ApiResponse.error("An unexpected error occurred while processing discovery request", details),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
