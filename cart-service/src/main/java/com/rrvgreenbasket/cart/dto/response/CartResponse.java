
package com.rrvgreenbasket.cart.dto.response;

import com.rrvgreenbasket.cart.entity.CartStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CartResponse(

        String publicId,
        CartStatus status,
        List<CartItemResponse> items,
        Integer itemCount,
        BigDecimal totalAmount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt

) {
}
