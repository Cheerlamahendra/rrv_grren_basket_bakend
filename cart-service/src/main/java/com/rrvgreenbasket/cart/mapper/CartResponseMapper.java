
package com.rrvgreenbasket.cart.mapper;

import com.rrvgreenbasket.cart.dto.response.CartItemResponse;
import com.rrvgreenbasket.cart.dto.response.CartResponse;
import com.rrvgreenbasket.cart.entity.Cart;
import com.rrvgreenbasket.cart.entity.CartItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class CartResponseMapper {

    public CartResponse toResponse(Cart cart) {

        List<CartItemResponse> items = cart.getItems()
                .stream()
                .map(this::toItemResponse)
                .toList();

        BigDecimal totalAmount = items.stream()
                .map(CartItemResponse::totalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartResponse(
                cart.getPublicId(),
                cart.getStatus(),
                items,
                items.size(),
                totalAmount,
                cart.getCreatedAt(),
                cart.getUpdatedAt()
        );
    }

    private CartItemResponse toItemResponse(CartItem item) {

        BigDecimal totalPrice = item.getPricePerKgSnapshot()
                .multiply(item.getQuantity());

        return new CartItemResponse(
                item.getPublicId(),
                item.getProductPublicId(),
                item.getProductNameSnapshot(),
                item.getProductImageUrlSnapshot(),
                item.getQuantity(),
                item.getUnitOfMeasure(),
                item.getPricePerKgSnapshot(),
                totalPrice
        );
    }
}
