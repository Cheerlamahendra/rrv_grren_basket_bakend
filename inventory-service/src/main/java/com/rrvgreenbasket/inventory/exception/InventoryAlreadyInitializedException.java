package com.rrvgreenbasket.inventory.exception;

public class InventoryAlreadyInitializedException extends DuplicateResourceException {
    public InventoryAlreadyInitializedException(String message) {
        super(message);
    }
}
