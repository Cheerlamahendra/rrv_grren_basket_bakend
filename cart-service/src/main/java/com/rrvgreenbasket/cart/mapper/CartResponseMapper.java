package com.rrvgreenbasket.cart.mapper;

import com.rrvgreenbasket.cart.dto.client.ProductDetailResponse;
import com.rrvgreenbasket.cart.dto.client.StockAvailabilityResponse;
import com.rrvgreenbasket.cart.dto.response.CartItemResponse;
import com.rrvgreenbasket.cart.dto.response.CartResponse;
import com.rrvgreenbasket.cart.entity.Cart;
import com.rrvgreenbasket.cart.entity.CartItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class CartResponseMapper {

    public CartResponse toResponse(Cart cart) {
        return toResponse(cart, Collections.emptyMap(), Collections.emptyMap());
    }

    public CartResponse toResponse(
            Cart cart,
            Map<String, ProductDetailResponse> liveProductMap,
            Map<String, StockAvailabilityResponse> liveStockMap) {

        List<CartItem> cartItems = (cart != null && cart.getItems() != null) ? cart.getItems() : Collections.emptyList();
        List<CartItemResponse> items = cartItems.stream()
                .map(item -> toItemResponse(
                        item,
                        liveProductMap != null ? liveProductMap.get(item.getProductPublicId()) : null,
                        liveStockMap != null ? liveStockMap.get(item.getProductPublicId()) : null
                ))
                .toList();

        BigDecimal currentSubtotal = items.stream()
                .map(CartItemResponse::getItemSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal previousSubtotal = items.stream()
                .map(item -> item.getPreviousUnitPrice()
                        .multiply(item.getQuantity())
                        .setScale(2, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        boolean cartPriceChanged = previousSubtotal.compareTo(currentSubtotal) != 0;
        String currency = cart.getCurrency() != null ? cart.getCurrency() : "INR";

        return CartResponse.builder()
                .cartPublicId(cart.getPublicId())
                .publicId(cart.getPublicId())
                .status(cart.getStatus())
                .items(items)
                .totalItems(items.size())
                .itemCount(items.size())
                .subtotal(currentSubtotal)
                .totalAmount(currentSubtotal)
                .previousSubtotal(previousSubtotal)
                .currentSubtotal(currentSubtotal)
                .currency(currency)
                .priceChanged(cartPriceChanged)
                .createdAt(cart.getCreatedAt())
                .updatedAt(cart.getUpdatedAt())
                .build();
    }

    private CartItemResponse toItemResponse(
            CartItem item,
            ProductDetailResponse liveProduct,
            StockAvailabilityResponse liveStock) {

        BigDecimal snapshotPrice = item.getPricePerKgSnapshot().setScale(2, RoundingMode.HALF_UP);
        BigDecimal currentPrice = (liveProduct != null && liveProduct.pricePerKg() != null)
                ? liveProduct.pricePerKg().setScale(2, RoundingMode.HALF_UP)
                : snapshotPrice;

        boolean priceChanged = snapshotPrice.compareTo(currentPrice) != 0;

        BigDecimal itemSubtotal = currentPrice
                .multiply(item.getQuantity())
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal availableQuantity = (liveStock != null && liveStock.availableQuantity() != null)
                ? liveStock.availableQuantity()
                : null;

        Boolean inStock = liveStock != null ? liveStock.inStock() : null;
        Boolean sufficient = (liveStock != null && liveStock.availableQuantity() != null)
                ? liveStock.availableQuantity().compareTo(item.getQuantity()) >= 0
                : null;

        String productName = (liveProduct != null && liveProduct.name() != null)
                ? liveProduct.name()
                : item.getProductNameSnapshot();

        String productImage = (liveProduct != null && liveProduct.imageUrl() != null)
                ? liveProduct.imageUrl()
                : item.getProductImageUrlSnapshot();

        return CartItemResponse.builder()
                .itemPublicId(item.getPublicId())
                .publicId(item.getPublicId())
                .productPublicId(item.getProductPublicId())
                .productName(productName)
                .productImage(productImage)
                .productImageUrl(productImage)
                .quantity(item.getQuantity().setScale(3, RoundingMode.HALF_UP))
                .quantityKg(item.getQuantity().setScale(3, RoundingMode.HALF_UP))
                .unitOfMeasure(item.getUnitOfMeasure())
                .previousUnitPrice(snapshotPrice)
                .pricePerKg(snapshotPrice)
                .currentUnitPrice(currentPrice)
                .priceChanged(priceChanged)
                .itemSubtotal(itemSubtotal)
                .totalPrice(itemSubtotal)
                .availableQuantity(availableQuantity)
                .inStock(inStock)
                .sufficient(sufficient)
                .build();
    }
}
