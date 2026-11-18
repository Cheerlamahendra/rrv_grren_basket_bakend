package com.rrvgreenbasket.cart.service;

import com.rrvgreenbasket.cart.dto.request.AddCartItemRequest;
import com.rrvgreenbasket.cart.dto.request.UpdateCartItemRequest;
import com.rrvgreenbasket.cart.dto.response.CartResponse;
import com.rrvgreenbasket.cart.dto.response.CartValidationResponse;

public interface CartService {

    /**
     * Retrieves the customer's active cart with live price and availability refresh.
     */
    CartResponse getMyCart(String customerPublicId);

    /**
     * Adds an item to the customer's active cart, merging quantities if duplicate product+unit exists.
     */
    CartResponse addItem(String customerPublicId, AddCartItemRequest request);

    /**
     * Updates an existing cart item's quantity after validating against Product and Inventory services.
     */
    CartResponse updateItem(String customerPublicId, String cartItemPublicId, UpdateCartItemRequest request);

    /**
     * Removes an item from the customer's active cart.
     */
    CartResponse removeItem(String customerPublicId, String cartItemPublicId);

    /**
     * Removes all items from the customer's active cart.
     */
    CartResponse clearCart(String customerPublicId);

    /**
     * Validates the customer's cart: checks product existence, active status, unit, live price changes,
     * and stock sufficiency without reserving inventory.
     */
    CartValidationResponse validateCart(String customerPublicId);

    /**
     * Scheduled batch process marking expired/abandoned active carts as EXPIRED.
     */
    int expireAbandonedCarts();
}
