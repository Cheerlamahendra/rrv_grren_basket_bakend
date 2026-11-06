package com.rrvgreenbasket.product.service.integration;

import com.rrvgreenbasket.product.enums.StockStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAvailabilityResult {

    private String productPublicId;
    private boolean isAvailable;
    private BigDecimal availableQuantityKg;
    private StockStatus stockStatus;
    private String reason;

    public static StockAvailabilityResult available(String productPublicId, BigDecimal stockKg) {
        return StockAvailabilityResult.builder()
                .productPublicId(productPublicId)
                .isAvailable(true)
                .availableQuantityKg(stockKg)
                .stockStatus(StockStatus.IN_STOCK)
                .reason("Stock is sufficient")
                .build();
    }

    public static StockAvailabilityResult insufficient(String productPublicId, BigDecimal stockKg, BigDecimal requestedKg) {
        return StockAvailabilityResult.builder()
                .productPublicId(productPublicId)
                .isAvailable(false)
                .availableQuantityKg(stockKg)
                .stockStatus(StockStatus.OUT_OF_STOCK)
                .reason(String.format("Insufficient stock: %.3f KG available, %.3f KG requested",
                        stockKg != null ? stockKg : BigDecimal.ZERO, requestedKg))
                .build();
    }

    public static StockAvailabilityResult unknown(String productPublicId) {
        return StockAvailabilityResult.builder()
                .productPublicId(productPublicId)
                .isAvailable(false)
                .availableQuantityKg(BigDecimal.ZERO)
                .stockStatus(StockStatus.UNKNOWN)
                .reason("Inventory stock unknown or service unavailable")
                .build();
    }
}
