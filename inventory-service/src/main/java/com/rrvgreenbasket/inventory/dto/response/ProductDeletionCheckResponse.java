package com.rrvgreenbasket.inventory.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDeletionCheckResponse {

    private String productPublicId;
    private boolean canDelete;
    private String reason;
    private BigDecimal physicalStock;
    private BigDecimal reservedStock;
    private Long movementCount;
}
