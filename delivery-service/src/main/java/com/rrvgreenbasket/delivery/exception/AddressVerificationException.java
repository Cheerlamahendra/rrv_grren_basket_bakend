package com.rrvgreenbasket.delivery.exception;

public class AddressVerificationException extends RuntimeException {
    public AddressVerificationException(String message) {
        super(message);
    }

    public AddressVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
