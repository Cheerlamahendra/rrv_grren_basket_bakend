package com.rrvgreenbasket.inventory.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rrvgreenbasket.inventory.entity.InventoryItem;
import com.rrvgreenbasket.inventory.enums.StockStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InventoryDetailResponse {

    private String publicId;
    private String productPublicId;
    private String unitOfMeasure;
    private BigDecimal physicalQuantity;
    private BigDecimal reservedQuantity;
    private BigDecimal availableQuantity;
    private BigDecimal lowStockThreshold;
    private Boolean initialized;
    private StockStatus stockStatus;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

    public static InventoryDetailResponse fromEntity(InventoryItem item) {
        if (item == null) {
            return null;
        }
        return InventoryDetailResponse.builder()
                .publicId(item.getPublicId())
                .productPublicId(item.getProductPublicId())
                .unitOfMeasure(item.getUnitOfMeasure() != null ? item.getUnitOfMeasure().name() : null)
                .physicalQuantity(item.getPhysicalQuantity())
                .reservedQuantity(item.getReservedQuantity())
                .availableQuantity(item.getAvailableQuantity())
                .lowStockThreshold(item.getLowStockThreshold())
                .initialized(item.getInitialized())
                .stockStatus(item.calculateStockStatus())
                .version(item.getVersion())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }
}
