package com.rrvgreenbasket.customer.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * DuplicateResourceException
 *
 * <p><strong>What it does:</strong></p>
 * Thrown when attempting to create a resource with a unique constraint collision
 * (e.g., duplicate mobile number, duplicate email, duplicate profile for auth user).
 * Maps to HTTP 409 Conflict.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
