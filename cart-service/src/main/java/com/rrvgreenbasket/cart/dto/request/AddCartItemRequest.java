
package com.rrvgreenbasket.cart.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AddCartItemRequest(

        @NotBlank(message = "Product public ID is required")
        String productPublicId,

        @NotNull(message = "Quantity is required")
        @DecimalMin(value = "0.000", inclusive = false,
                message = "Quantity must be greater than zero")
        @Digits(integer = 9, fraction = 3,
                message = "Quantity can have up to 3 decimal places")
        BigDecimal quantityKg

) {
}
