package com.rrvgreenbasket.auth.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * ValidationException
 *
 * <p><strong>What it does:</strong></p>
 * Thrown when business-layer field validation fails (e.g., password mismatch
 * in the forgot-password reset flow).
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
