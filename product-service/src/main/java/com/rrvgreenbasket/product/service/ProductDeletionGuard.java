package com.rrvgreenbasket.product.service;

public interface ProductDeletionGuard {

    void validateProductCanBeDeleted(String productPublicId);
}