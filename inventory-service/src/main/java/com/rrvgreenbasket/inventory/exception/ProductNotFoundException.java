package com.rrvgreenbasket.inventory.exception;

public class ProductNotFoundException extends ResourceNotFoundException {
    public ProductNotFoundException(String message) {
        super(message);
    }
}
