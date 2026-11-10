
package com.rrvgreenbasket.cart.dto.response;

import java.math.BigDecimal;

public record CartItemResponse(

        String publicId,
        String productPublicId,
        String productName,
        String productImageUrl,
        BigDecimal quantityKg,
        String unitOfMeasure,
        BigDecimal pricePerKg,
        BigDecimal totalPrice

) {
}

