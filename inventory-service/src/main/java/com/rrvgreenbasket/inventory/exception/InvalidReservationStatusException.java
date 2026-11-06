package com.rrvgreenbasket.inventory.exception;

public class InvalidReservationStatusException extends DuplicateResourceException {
    public InvalidReservationStatusException(String message) {
        super(message);
    }
}
