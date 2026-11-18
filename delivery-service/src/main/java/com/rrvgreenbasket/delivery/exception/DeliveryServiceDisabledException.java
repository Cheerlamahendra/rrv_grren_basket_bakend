package com.rrvgreenbasket.delivery.exception;

public class DeliveryServiceDisabledException extends RuntimeException {
    public DeliveryServiceDisabledException(String message) {
        super(message);
    }
}
