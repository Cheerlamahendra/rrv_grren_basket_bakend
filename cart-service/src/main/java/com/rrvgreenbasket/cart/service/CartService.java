package com.rrvgreenbasket.cart.service;

import com.rrvgreenbasket.cart.dto.request.AddCartItemRequest;
import com.rrvgreenbasket.cart.dto.request.UpdateCartItemRequest;
import com.rrvgreenbasket.cart.dto.response.CartResponse;

public interface CartService {

    CartResponse getMyCart(String customerPublicId);

    CartResponse addItem(
            String customerPublicId,
            AddCartItemRequest request
    );

    CartResponse updateItem(
            String customerPublicId,
            String cartItemPublicId,
            UpdateCartItemRequest request
    );

    CartResponse removeItem(
            String customerPublicId,
            String cartItemPublicId
    );
    CartResponse clearCart(String customerPublicId);
}