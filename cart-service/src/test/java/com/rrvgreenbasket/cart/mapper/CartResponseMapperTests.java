package com.rrvgreenbasket.cart.mapper;

import com.rrvgreenbasket.cart.dto.client.ProductDetailResponse;
import com.rrvgreenbasket.cart.dto.client.StockAvailabilityResponse;
import com.rrvgreenbasket.cart.dto.response.CartItemResponse;
import com.rrvgreenbasket.cart.dto.response.CartResponse;
import com.rrvgreenbasket.cart.entity.Cart;
import com.rrvgreenbasket.cart.entity.CartItem;
import com.rrvgreenbasket.cart.entity.CartStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CartResponseMapper Tests")
class CartResponseMapperTests {

    private CartResponseMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new CartResponseMapper();
    }

    @Test
    @DisplayName("Maps cart using snapshot prices when live prices are absent")
    void testToResponse_WithSnapshotPrices() {
        Cart cart = new Cart();
        cart.setPublicId(UUID.randomUUID().toString());
        cart.setCustomerPublicId("cust-1");
        cart.setStatus(CartStatus.ACTIVE);
        cart.setCurrency("INR");

        CartItem item = new CartItem();
        item.setPublicId(UUID.randomUUID().toString());
        item.setProductPublicId("prod-1");
        item.setProductNameSnapshot("Fresh Tomato");
        item.setProductImageUrlSnapshot("http://example.com/tomato.png");
        item.setQuantity(new BigDecimal("2.500"));
        item.setUnitOfMeasure("KG");
        item.setPricePerKgSnapshot(new BigDecimal("40.00"));
        cart.addItem(item);

        CartResponse response = mapper.toResponse(cart);

        assertNotNull(response);
        assertEquals(cart.getPublicId(), response.getCartPublicId());
        assertEquals(1, response.getTotalItems());
        assertEquals(new BigDecimal("100.00"), response.getSubtotal()); // 2.5 * 40.00 = 100.00
        assertFalse(response.isPriceChanged());

        CartItemResponse itemResp = response.getItems().get(0);
        assertEquals("Fresh Tomato", itemResp.getProductName());
        assertEquals(new BigDecimal("2.500"), itemResp.getQuantity());
        assertEquals(new BigDecimal("40.00"), itemResp.getPreviousUnitPrice());
        assertEquals(new BigDecimal("40.00"), itemResp.getCurrentUnitPrice());
        assertEquals(new BigDecimal("100.00"), itemResp.getItemSubtotal());
        assertFalse(itemResp.isPriceChanged());
    }

    @Test
    @DisplayName("Detects live price change and updates subtotals and flags")
    void testToResponse_WithLivePriceChange() {
        Cart cart = new Cart();
        cart.setPublicId(UUID.randomUUID().toString());
        cart.setCustomerPublicId("cust-1");
        cart.setStatus(CartStatus.ACTIVE);
        cart.setCurrency("INR");

        CartItem item = new CartItem();
        item.setPublicId(UUID.randomUUID().toString());
        item.setProductPublicId("prod-1");
        item.setProductNameSnapshot("Fresh Tomato");
        item.setProductImageUrlSnapshot("http://example.com/tomato.png");
        item.setQuantity(new BigDecimal("2.000"));
        item.setUnitOfMeasure("KG");
        item.setPricePerKgSnapshot(new BigDecimal("40.00")); // Stored snapshot: 40
        cart.addItem(item);

        // Downstream live price updated to 50.00
        ProductDetailResponse liveProduct = new ProductDetailResponse(
                "prod-1", "Organic Tomato", new BigDecimal("50.00"),
                BigDecimal.ONE, "KG", new BigDecimal("50.00"),
                "http://example.com/tomato.png", "ACTIVE", true, false, true, "IN_STOCK"
        );

        StockAvailabilityResponse liveStock = new StockAvailabilityResponse(
                "prod-1", "KG", new BigDecimal("100"), BigDecimal.ZERO,
                new BigDecimal("100"), new BigDecimal("2.000"), true, true, "IN_STOCK", false
        );

        CartResponse response = mapper.toResponse(cart, Map.of("prod-1", liveProduct), Map.of("prod-1", liveStock));

        assertNotNull(response);
        assertTrue(response.isPriceChanged(), "Cart priceChanged flag must be true");
        assertEquals(new BigDecimal("80.00"), response.getPreviousSubtotal()); // 2.0 * 40.00 = 80.00
        assertEquals(new BigDecimal("100.00"), response.getCurrentSubtotal()); // 2.0 * 50.00 = 100.00
        assertEquals(new BigDecimal("100.00"), response.getSubtotal());

        CartItemResponse itemResp = response.getItems().get(0);
        assertTrue(itemResp.isPriceChanged());
        assertEquals(new BigDecimal("40.00"), itemResp.getPreviousUnitPrice());
        assertEquals(new BigDecimal("50.00"), itemResp.getCurrentUnitPrice());
        assertEquals(new BigDecimal("100.00"), itemResp.getItemSubtotal());
        assertTrue(itemResp.getSufficient());
        assertTrue(itemResp.getInStock());
    }
}
