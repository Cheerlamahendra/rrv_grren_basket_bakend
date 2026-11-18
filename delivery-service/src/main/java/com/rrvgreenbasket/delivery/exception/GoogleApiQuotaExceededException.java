package com.rrvgreenbasket.delivery.exception;

public class GoogleApiQuotaExceededException extends RuntimeException {
    public GoogleApiQuotaExceededException(String message) {
        super(message);
    }
}
