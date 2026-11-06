package com.rrvgreenbasket.customer.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * InvalidRequestException
 *
 * <p><strong>What it does:</strong></p>
 * Thrown when an incoming request fails domain-level validation checks
 * (e.g., maximum address count reached, trying to delete the only default address).
 * Maps to HTTP 400 Bad Request.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
