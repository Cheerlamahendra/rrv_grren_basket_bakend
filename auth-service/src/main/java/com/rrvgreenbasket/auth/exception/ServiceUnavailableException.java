package com.rrvgreenbasket.auth.exception;

/**
 * ServiceUnavailableException
 *
 * <p><strong>What it does:</strong></p>
 * Thrown when downstream dependencies (e.g. Customer Service) are temporarily unavailable
 * during cross-service operations.
 */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }
}
