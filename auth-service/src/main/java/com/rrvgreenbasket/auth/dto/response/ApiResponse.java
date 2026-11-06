package com.rrvgreenbasket.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Standard API Response Envelope
 *
 * <p><strong>Structure:</strong></p>
 * <pre>
 * {
 *   "success": true,
 *   "message": "Operation completed successfully",
 *   "data": { ... },
 *   "timestamp": "2026-10-30T10:30:00.000Z"
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;
    @Builder.Default
    private Instant timestamp = Instant.now();

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<T> success(String message) {
        return success(message, null);
    }

    public static <T> ApiResponse<T> error(String message, T errorDetails) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .data(errorDetails)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<T> error(String message) {
        return error(message, null);
    }
}
