package com.rrvgreenbasket.inventory.exception;

public class InsufficientStockException extends InvalidRequestException {
    public InsufficientStockException(String message) {
        super(message);
    }
}
