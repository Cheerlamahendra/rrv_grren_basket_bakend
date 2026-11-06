package com.rrvgreenbasket.inventory.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rrvgreenbasket.inventory.entity.InventoryItem;
import com.rrvgreenbasket.inventory.enums.StockStatus;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StockAvailabilityResponse {

    private String productPublicId;
    private String unitOfMeasure;
    private BigDecimal physicalQuantity;
    private BigDecimal reservedQuantity;
    private BigDecimal availableQuantity;
    private BigDecimal requestedQuantity;
    private Boolean sufficient;
    private boolean inStock;
    private StockStatus stockStatus;
    private boolean lowStock;

    public static StockAvailabilityResponse fromEntity(InventoryItem item) {
        return fromEntity(item, BigDecimal.ONE.setScale(3));
    }

    public static StockAvailabilityResponse fromEntity(InventoryItem item, BigDecimal requestedQuantity) {
        if (item == null) {
            return null;
        }
        StockStatus status = item.calculateStockStatus();
        BigDecimal requested = requestedQuantity != null ? requestedQuantity : BigDecimal.ONE.setScale(3);
        boolean isSufficient = item.getAvailableQuantity() != null && item.getAvailableQuantity().compareTo(requested) >= 0;

        return StockAvailabilityResponse.builder()
                .productPublicId(item.getProductPublicId())
                .unitOfMeasure(item.getUnitOfMeasure() != null ? item.getUnitOfMeasure().name() : null)
                .physicalQuantity(item.getPhysicalQuantity())
                .reservedQuantity(item.getReservedQuantity())
                .availableQuantity(item.getAvailableQuantity())
                .requestedQuantity(requested)
                .sufficient(isSufficient)
                .inStock(status != StockStatus.OUT_OF_STOCK)
                .stockStatus(status)
                .lowStock(status == StockStatus.LOW_STOCK)
                .build();
    }
}
