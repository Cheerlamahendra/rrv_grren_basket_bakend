package com.rrvgreenbasket.cart.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddCartItemRequest {

    @NotBlank(message = "Product public ID is required")
    private String productPublicId;

    @DecimalMin(value = "0.000", inclusive = false, message = "Quantity must be greater than zero")
    @Digits(integer = 9, fraction = 3, message = "Quantity can have up to 3 decimal places")
    private BigDecimal quantity;

    @DecimalMin(value = "0.000", inclusive = false, message = "Quantity must be greater than zero")
    @Digits(integer = 9, fraction = 3, message = "Quantity can have up to 3 decimal places")
    private BigDecimal quantityKg;

    private String unitOfMeasure;

    @JsonIgnore
    public BigDecimal getEffectiveQuantity() {
        if (quantity != null) {
            return quantity;
        }
        return quantityKg;
    }

    @AssertTrue(message = "Quantity is required and must be greater than zero")
    @JsonIgnore
    public boolean isValidQuantity() {
        BigDecimal effective = getEffectiveQuantity();
        return effective != null && effective.compareTo(BigDecimal.ZERO) > 0;
    }
}
