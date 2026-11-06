package com.rrvgreenbasket.customer.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * ForbiddenOperationException
 *
 * <p><strong>What it does:</strong></p>
 * Thrown when an authenticated user attempts to access or modify a resource
 * belonging to another customer without possessing ADMIN privileges.
 * Maps to HTTP 403 Forbidden.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class ForbiddenOperationException extends RuntimeException {

    public ForbiddenOperationException(String message) {
        super(message);
    }
}
