package com.rrvgreenbasket.cart.dto.response;

public enum CartItemValidationStatus {
    VALID,
    PRICE_CHANGED,
    PRODUCT_UNAVAILABLE,
    OUT_OF_STOCK,
    INSUFFICIENT_STOCK,
    INVALID_UNIT
}
