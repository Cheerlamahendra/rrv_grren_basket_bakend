package com.rrvgreenbasket.order.client;

import com.rrvgreenbasket.order.client.dto.CartClientResponse;
import com.rrvgreenbasket.order.client.dto.CartValidationClientResponse;

public interface CartServiceClient {

    CartClientResponse getCustomerCart(String customerPublicId);

    CartValidationClientResponse validateCart(String customerPublicId);

    void clearCart(String customerPublicId);
}
