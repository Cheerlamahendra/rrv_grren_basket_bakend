
package com.rrvgreenbasket.cart.dto.client;

import java.math.BigDecimal;

public record ProductDetailResponse(
        String publicId,
        String name,
        BigDecimal pricePerKg,
        BigDecimal defaultQuantityKg,
        String quantityUnit,
        BigDecimal totalPrice,
        String imageUrl,
        String status,
        Boolean adminEnabled,
        Boolean isFeatured,
        Boolean available,
        String stockStatus
) {
}
