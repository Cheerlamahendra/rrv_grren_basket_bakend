package com.rrvgreenbasket.config.exception;

import com.rrvgreenbasket.config.dto.response.ApiResponse;
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
 * Centralized exception handling interceptor for the Config Server.
 * Sanitizes errors and returns consistent {@link ApiResponse} envelopes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ConfigurationNotFoundException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleConfigurationNotFoundException(
            ConfigurationNotFoundException ex, WebRequest request) {
        log.warn("Configuration not found: {}", ex.getMessage());
        Map<String, String> details = new HashMap<>();
        details.put("path", request.getDescription(false).replace("uri=", ""));
        return new ResponseEntity<>(ApiResponse.error(ex.getMessage(), details), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleGlobalException(
            Exception ex, WebRequest request) {
        log.error("Unhandled Config Server exception: ", ex);
        Map<String, String> details = new HashMap<>();
        details.put("error", ex.getClass().getSimpleName());
        details.put("path", request.getDescription(false).replace("uri=", ""));
        return new ResponseEntity<>(
                ApiResponse.error("An error occurred while processing the configuration request", details),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
