package com.rrvgreenbasket.order.client;

import com.rrvgreenbasket.order.client.dto.ProductDetailClientResponse;

public interface ProductServiceClient {
    ProductDetailClientResponse getProductDetails(String productPublicId);
}
