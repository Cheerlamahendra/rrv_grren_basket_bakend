package com.rrvgreenbasket.auth.exception;

/**
 * DuplicateResourceException
 *
 * <p><strong>What it does:</strong></p>
 * Thrown when an entity creation conflicts with an existing unique constraint (e.g., duplicate email).
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
