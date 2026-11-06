package com.rrvgreenbasket.inventory.exception;

public class ReservationNotFoundException extends ResourceNotFoundException {
    public ReservationNotFoundException(String message) {
        super(message);
    }
}
