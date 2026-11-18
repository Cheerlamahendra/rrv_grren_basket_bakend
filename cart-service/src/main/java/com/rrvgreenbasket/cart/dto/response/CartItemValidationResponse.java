package com.rrvgreenbasket.cart.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CartItemValidationResponse {

    private String itemPublicId;
    private String productPublicId;
    private String productName;
    private BigDecimal quantity;
    private String unitOfMeasure;
    private BigDecimal previousUnitPrice;
    private BigDecimal currentUnitPrice;
    private boolean priceChanged;
    private BigDecimal availableQuantity;
    private boolean inStock;
    private boolean sufficient;
    private BigDecimal itemSubtotal;
    private CartItemValidationStatus status;
    private String message;
}
