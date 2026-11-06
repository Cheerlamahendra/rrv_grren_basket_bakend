package com.rrvgreenbasket.discovery.exception;

/**
 * ResourceNotFoundException
 *
 * <p><strong>What it does:</strong></p>
 * Custom unchecked exception thrown when a requested resource (such as a named service)
 * does not exist in the Eureka service registry.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s not found with %s : '%s'", resourceName, fieldName, fieldValue));
    }
}
