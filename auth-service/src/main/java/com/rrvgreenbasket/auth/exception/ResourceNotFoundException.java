package com.rrvgreenbasket.auth.exception;

/**
 * ResourceNotFoundException
 *
 * <p><strong>What it does:</strong></p>
 * Thrown when an expected entity (User, Role, etc.) is missing from the database.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
