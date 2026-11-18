package com.rrvgreenbasket.cart.repository;

import com.rrvgreenbasket.cart.entity.Cart;
import com.rrvgreenbasket.cart.entity.CartItem;
import com.rrvgreenbasket.cart.entity.CartStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.cloud.config.enabled=false",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("test")
@DisplayName("CartRepository JPA Integration Tests")
class CartRepositoryTests {

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Test
    @DisplayName("Persists cart and cascades cart items properly")
    void testSaveCartWithItems() {
        Cart cart = new Cart();
        cart.setCustomerPublicId("cust-repo-1");
        cart.setStatus(CartStatus.ACTIVE);
        cart.setCurrency("INR");
        cart.setExpiresAt(LocalDateTime.now().plusDays(7));

        CartItem item = new CartItem();
        item.setProductPublicId(UUID.randomUUID().toString());
        item.setProductNameSnapshot("Organic Carrot");
        item.setProductImageUrlSnapshot("http://example.com/carrot.png");
        item.setQuantity(new BigDecimal("3.000"));
        item.setUnitOfMeasure("KG");
        item.setPricePerKgSnapshot(new BigDecimal("50.00"));
        cart.addItem(item);

        Cart saved = cartRepository.save(cart);

        assertNotNull(saved.getId());
        assertNotNull(saved.getPublicId());
        assertEquals(1, saved.getItems().size());
        assertNotNull(saved.getItems().get(0).getId());
        assertNotNull(saved.getItems().get(0).getPublicId());

        Optional<Cart> found = cartRepository.findByCustomerPublicIdAndStatus("cust-repo-1", CartStatus.ACTIVE);
        assertTrue(found.isPresent());
        assertEquals("INR", found.get().getCurrency());
    }

    @Test
    @DisplayName("Finds expired carts correctly before threshold")
    void testFindExpiredCarts() {
        Cart cart = new Cart();
        cart.setCustomerPublicId("cust-expired");
        cart.setStatus(CartStatus.ACTIVE);
        cart.setExpiresAt(LocalDateTime.now().minusHours(2)); // Expired 2 hours ago
        cartRepository.save(cart);

        Page<Cart> expired = cartRepository.findByStatusAndExpiresAtBefore(
                CartStatus.ACTIVE,
                LocalDateTime.now(),
                PageRequest.of(0, 10)
        );

        assertTrue(expired.getContent().stream().anyMatch(c -> c.getCustomerPublicId().equals("cust-expired")));
    }
}
