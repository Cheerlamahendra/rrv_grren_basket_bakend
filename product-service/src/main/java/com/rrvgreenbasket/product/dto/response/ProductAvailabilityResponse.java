package com.rrvgreenbasket.product.dto.response;

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
public class ProductAvailabilityResponse {

    private String publicId;
    private String name;
    private Boolean available;
    private Boolean adminEnabled;
    private String productStatus;
    private StockStatus stockStatus;
    private BigDecimal requestedQuantityKg;
    private BigDecimal pricePerKg;
    private BigDecimal totalPrice;
    private String reason;
}
