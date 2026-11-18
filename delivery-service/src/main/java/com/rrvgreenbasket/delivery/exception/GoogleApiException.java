package com.rrvgreenbasket.delivery.exception;

public class GoogleApiException extends RuntimeException {
    public GoogleApiException(String message) {
        super(message);
    }

    public GoogleApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
