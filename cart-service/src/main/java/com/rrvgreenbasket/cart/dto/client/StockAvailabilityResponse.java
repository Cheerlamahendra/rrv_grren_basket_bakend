
package com.rrvgreenbasket.cart.dto.client;

import java.math.BigDecimal;

public record StockAvailabilityResponse(
        String productPublicId,
        String unitOfMeasure,
        BigDecimal physicalQuantity,
        BigDecimal reservedQuantity,
        BigDecimal availableQuantity,
        BigDecimal requestedQuantity,
        Boolean sufficient,
        Boolean inStock,
        String stockStatus,
        Boolean lowStock
) {
}
