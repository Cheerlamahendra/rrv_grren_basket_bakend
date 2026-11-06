package com.rrvgreenbasket.inventory.client;

import com.rrvgreenbasket.inventory.client.dto.ProductVerificationResponse;

public interface ProductServiceClient {

    ProductVerificationResponse getProductByPublicId(String productPublicId);
}
