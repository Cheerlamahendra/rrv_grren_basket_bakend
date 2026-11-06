package com.rrvgreenbasket.inventory.exception;

public class IdempotencyConflictException extends DuplicateResourceException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
