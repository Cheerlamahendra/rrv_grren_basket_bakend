package com.rrvgreenbasket.wishlist;

import com.rrvgreenbasket.wishlist.entity.Wishlist;
import com.rrvgreenbasket.wishlist.entity.WishlistItem;
import com.rrvgreenbasket.wishlist.repository.WishlistItemRepository;
import com.rrvgreenbasket.wishlist.repository.WishlistRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("JPA & Flyway Schema Tests — Wishlist Database")
class WishlistRepositoryTests {

    @Autowired
    private WishlistRepository wishlistRepository;

    @Autowired
    private WishlistItemRepository wishlistItemRepository;

    @Autowired
    private TestEntityManager entityManager;

    private static final String CUSTOMER_ID = "550e8400-e29b-41d4-a716-446655440001";
    private static final String PRODUCT_1 = "660e8400-e29b-41d4-a716-446655440001";
    private static final String PRODUCT_2 = "660e8400-e29b-41d4-a716-446655440002";

    @Test
    @DisplayName("Should successfully persist Wishlist and WishlistItem with Flyway schema")
    void testPersistWishlistAndItems() {
        Wishlist wishlist = Wishlist.builder()
                .wishlistPublicId(UUID.randomUUID().toString())
                .customerPublicId(CUSTOMER_ID)
                .build();

        Wishlist savedWishlist = wishlistRepository.save(wishlist);
        assertNotNull(savedWishlist.getId());

        WishlistItem item1 = WishlistItem.builder()
                .wishlist(savedWishlist)
                .productPublicId(PRODUCT_1)
                .build();

        savedWishlist.addItem(item1);
        wishlistItemRepository.save(item1);

        entityManager.flush();
        entityManager.clear();

        Optional<Wishlist> found = wishlistRepository.findByCustomerPublicIdWithItems(CUSTOMER_ID);
        assertTrue(found.isPresent());
        assertEquals(1, found.get().getItems().size());
        assertEquals(PRODUCT_1, found.get().getItems().get(0).getProductPublicId());
    }

    @Test
    @DisplayName("Should enforce unique constraint on (wishlist_id, product_public_id)")
    void testUniqueConstraintOnWishlistProduct() {
        Wishlist wishlist = Wishlist.builder()
                .wishlistPublicId(UUID.randomUUID().toString())
                .customerPublicId("550e8400-e29b-41d4-a716-446655440002")
                .build();
        Wishlist savedWishlist = wishlistRepository.saveAndFlush(wishlist);

        WishlistItem item1 = WishlistItem.builder()
                .wishlist(savedWishlist)
                .productPublicId(PRODUCT_1)
                .build();
        wishlistItemRepository.saveAndFlush(item1);

        // Attempt duplicate insert of same product in same wishlist
        WishlistItem duplicateItem = WishlistItem.builder()
                .wishlist(savedWishlist)
                .productPublicId(PRODUCT_1)
                .build();

        assertThrows(DataIntegrityViolationException.class, () ->
                wishlistItemRepository.saveAndFlush(duplicateItem));
    }

    @Test
    @DisplayName("Should cascade delete wishlist items when wishlist is deleted")
    void testCascadeDelete() {
        Wishlist wishlist = Wishlist.builder()
                .wishlistPublicId(UUID.randomUUID().toString())
                .customerPublicId("550e8400-e29b-41d4-a716-446655440003")
                .build();
        Wishlist savedWishlist = wishlistRepository.saveAndFlush(wishlist);

        WishlistItem item1 = WishlistItem.builder()
                .wishlist(savedWishlist)
                .productPublicId(PRODUCT_1)
                .build();
        WishlistItem item2 = WishlistItem.builder()
                .wishlist(savedWishlist)
                .productPublicId(PRODUCT_2)
                .build();

        wishlistItemRepository.saveAllAndFlush(List.of(item1, item2));

        assertEquals(2, wishlistItemRepository.countByWishlistId(savedWishlist.getId()));

        // Delete parent wishlist
        wishlistRepository.delete(savedWishlist);
        wishlistRepository.flush();

        // Items should be cascade-deleted
        assertEquals(0, wishlistItemRepository.countByWishlistId(savedWishlist.getId()));
    }

    @Test
    @DisplayName("Should query bulk existing product public IDs")
    void testFindExistingProductPublicIds() {
        String customerId = "550e8400-e29b-41d4-a716-446655440004";
        Wishlist wishlist = Wishlist.builder()
                .wishlistPublicId(UUID.randomUUID().toString())
                .customerPublicId(customerId)
                .build();
        Wishlist savedWishlist = wishlistRepository.saveAndFlush(wishlist);

        WishlistItem item1 = WishlistItem.builder()
                .wishlist(savedWishlist)
                .productPublicId(PRODUCT_1)
                .build();
        wishlistItemRepository.saveAndFlush(item1);

        List<String> queryList = List.of(PRODUCT_1, PRODUCT_2, "660e8400-e29b-41d4-a716-446655440003");
        List<String> existing = wishlistItemRepository.findExistingProductPublicIdsByCustomerAndProducts(customerId, queryList);

        assertEquals(1, existing.size());
        assertEquals(PRODUCT_1, existing.get(0));
    }
}
