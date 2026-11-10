package com.rrvgreenbasket.cart.exception;

import java.time.LocalDateTime;
import java.util.Map;

public record CartErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> validationErrors
) {
}