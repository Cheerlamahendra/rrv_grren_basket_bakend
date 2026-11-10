
package com.rrvgreenbasket.cart.dto.client;

import java.math.BigDecimal;

public record ProductAvailabilityResponse(
        String publicId,
        String name,
        Boolean available,
        Boolean adminEnabled,
        String productStatus,
        String stockStatus,
        BigDecimal requestedQuantityKg,
        BigDecimal pricePerKg,
        BigDecimal totalPrice,
        String reason
) {
}
