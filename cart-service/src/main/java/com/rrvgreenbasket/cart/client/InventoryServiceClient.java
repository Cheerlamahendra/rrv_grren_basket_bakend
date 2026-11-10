
package com.rrvgreenbasket.cart.client;

import com.rrvgreenbasket.cart.dto.client.StockAvailabilityResponse;

import java.math.BigDecimal;

public interface InventoryServiceClient {

    StockAvailabilityResponse checkStockAvailability(
            String productPublicId,
            BigDecimal quantity
    );
}
