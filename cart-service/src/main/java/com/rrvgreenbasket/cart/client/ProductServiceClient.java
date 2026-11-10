
package com.rrvgreenbasket.cart.client;

import com.rrvgreenbasket.cart.dto.client.ProductDetailResponse;
import com.rrvgreenbasket.cart.dto.client.ProductAvailabilityResponse;

import java.math.BigDecimal;

public interface ProductServiceClient {

    ProductDetailResponse getProductByPublicId(String productPublicId);

    ProductAvailabilityResponse checkProductAvailability(
            String productPublicId,
            BigDecimal quantity
    );
}
