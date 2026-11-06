package com.rrvgreenbasket.inventory.exception;

public class UnitMismatchException extends InvalidRequestException {
    public UnitMismatchException(String message) {
        super(message);
    }
}
