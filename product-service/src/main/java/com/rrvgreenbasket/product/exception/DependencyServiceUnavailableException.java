package com.rrvgreenbasket.product.exception;

public class DependencyServiceUnavailableException extends RuntimeException {

    public DependencyServiceUnavailableException(String message) {
        super(message);
    }

    public DependencyServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
