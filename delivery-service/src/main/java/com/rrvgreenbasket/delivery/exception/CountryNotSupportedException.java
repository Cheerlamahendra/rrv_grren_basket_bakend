package com.rrvgreenbasket.delivery.exception;

public class CountryNotSupportedException extends RuntimeException {
    public CountryNotSupportedException(String message) {
        super(message);
    }
}
