package com.rrvgreenbasket.wishlist;

import com.rrvgreenbasket.wishlist.client.ProductServiceClient;
import com.rrvgreenbasket.wishlist.dto.client.ProductDto;
import com.rrvgreenbasket.wishlist.dto.response.*;
import com.rrvgreenbasket.wishlist.entity.Wishlist;
import com.rrvgreenbasket.wishlist.entity.WishlistItem;
import com.rrvgreenbasket.wishlist.exception.InvalidRequestException;
import com.rrvgreenbasket.wishlist.exception.ResourceNotFoundException;
import com.rrvgreenbasket.wishlist.repository.WishlistItemRepository;
import com.rrvgreenbasket.wishlist.repository.WishlistRepository;
import com.rrvgreenbasket.wishlist.service.impl.WishlistServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WishlistServiceTests {

    @Mock
    private WishlistRepository wishlistRepository;

    @Mock
    private WishlistItemRepository wishlistItemRepository;

    @Mock
    private ProductServiceClient productServiceClient;

    @InjectMocks
    private WishlistServiceImpl wishlistService;

    private static final String CUSTOMER_A_ID = "550e8400-e29b-41d4-a716-446655440001";
    private static final String CUSTOMER_B_ID = "550e8400-e29b-41d4-a716-446655440002";
    private static final String PRODUCT_1_ID = "660e8400-e29b-41d4-a716-446655440001";
    private static final String PRODUCT_2_ID = "660e8400-e29b-41d4-a716-446655440002";

    private Wishlist sampleWishlist;

    @BeforeEach
    void setUp() {
        sampleWishlist = Wishlist.builder()
                .id(1L)
                .wishlistPublicId(UUID.randomUUID().toString())
                .customerPublicId(CUSTOMER_A_ID)
                .items(new ArrayList<>())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Should successfully add a product to wishlist for a first-time customer")
    void testAddProductToWishlist_FirstTimeCustomer() {
        ProductDto activeProduct = ProductDto.builder()
                .publicId(PRODUCT_1_ID)
                .name("Organic Tomato")
                .status("ACTIVE")
                .available(true)
                .adminEnabled(true)
                .build();

        when(productServiceClient.validateProductForWishlist(PRODUCT_1_ID)).thenReturn(activeProduct);
        when(wishlistRepository.findByCustomerPublicId(CUSTOMER_A_ID)).thenReturn(Optional.empty());
        when(wishlistRepository.save(any(Wishlist.class))).thenReturn(sampleWishlist);
        when(wishlistItemRepository.existsByWishlistIdAndProductPublicId(any(), eq(PRODUCT_1_ID))).thenReturn(false);

        WishlistItemResponse response = wishlistService.addProductToWishlist(CUSTOMER_A_ID, PRODUCT_1_ID);

        assertNotNull(response);
        assertEquals(PRODUCT_1_ID, response.getProductPublicId());
        assertTrue(response.getIsWishlisted());

        verify(productServiceClient, times(1)).validateProductForWishlist(PRODUCT_1_ID);
        verify(wishlistItemRepository, times(1)).save(any(WishlistItem.class));
    }

    @Test
    @DisplayName("Should handle duplicate add idempotently without creating duplicate items")
    void testAddProductToWishlist_DuplicateItem_Idempotent() {
        ProductDto activeProduct = ProductDto.builder()
                .publicId(PRODUCT_1_ID)
                .name("Organic Tomato")
                .status("ACTIVE")
                .available(true)
                .adminEnabled(true)
                .build();

        when(productServiceClient.validateProductForWishlist(PRODUCT_1_ID)).thenReturn(activeProduct);
        when(wishlistRepository.findByCustomerPublicId(CUSTOMER_A_ID)).thenReturn(Optional.of(sampleWishlist));
        when(wishlistItemRepository.existsByWishlistIdAndProductPublicId(sampleWishlist.getId(), PRODUCT_1_ID)).thenReturn(true);

        WishlistItemResponse response = wishlistService.addProductToWishlist(CUSTOMER_A_ID, PRODUCT_1_ID);

        assertNotNull(response);
        assertEquals(PRODUCT_1_ID, response.getProductPublicId());
        assertTrue(response.getIsWishlisted());

        // Save on item repo should NOT be called since item already existed
        verify(wishlistItemRepository, never()).save(any(WishlistItem.class));
    }

    @Test
    @DisplayName("Should reject adding non-existent product when Product Service throws ResourceNotFoundException")
    void testAddProductToWishlist_ProductNotFound() {
        when(productServiceClient.validateProductForWishlist(PRODUCT_1_ID))
                .thenThrow(new ResourceNotFoundException("Product not found with ID: " + PRODUCT_1_ID));

        assertThrows(ResourceNotFoundException.class, () ->
                wishlistService.addProductToWishlist(CUSTOMER_A_ID, PRODUCT_1_ID));

        verify(wishlistItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject adding inactive product when Product Service indicates inactivity")
    void testAddProductToWishlist_InactiveProduct() {
        when(productServiceClient.validateProductForWishlist(PRODUCT_1_ID))
                .thenThrow(new InvalidRequestException("Product is inactive or unavailable and cannot be wishlisted"));

        assertThrows(InvalidRequestException.class, () ->
                wishlistService.addProductToWishlist(CUSTOMER_A_ID, PRODUCT_1_ID));

        verify(wishlistItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should retrieve complete customer wishlist and enrich with real-time product details")
    void testGetCustomerWishlist_Success() {
        WishlistItem item1 = WishlistItem.builder()
                .id(101L)
                .wishlist(sampleWishlist)
                .productPublicId(PRODUCT_1_ID)
                .addedAt(Instant.now())
                .build();

        WishlistItem item2 = WishlistItem.builder()
                .id(102L)
                .wishlist(sampleWishlist)
                .productPublicId(PRODUCT_2_ID)
                .addedAt(Instant.now())
                .build();

        sampleWishlist.setItems(new ArrayList<>(List.of(item1, item2)));

        when(wishlistRepository.findByCustomerPublicIdWithItems(CUSTOMER_A_ID))
                .thenReturn(Optional.of(sampleWishlist));

        ProductDto product1 = ProductDto.builder()
                .publicId(PRODUCT_1_ID)
                .name("Fresh Tomato")
                .slug("fresh-tomato")
                .pricePerKg(BigDecimal.valueOf(40.00))
                .quantityUnit("KG")
                .imageUrl("https://cdn.example.com/tomato.jpg")
                .available(true)
                .stockStatus("IN_STOCK")
                .build();

        ProductDto product2 = ProductDto.builder()
                .publicId(PRODUCT_2_ID)
                .name("Red Onion")
                .slug("red-onion")
                .pricePerKg(BigDecimal.valueOf(35.00))
                .quantityUnit("KG")
                .imageUrl("https://cdn.example.com/onion.jpg")
                .available(true)
                .stockStatus("IN_STOCK")
                .build();

        when(productServiceClient.getProductDetails(PRODUCT_1_ID)).thenReturn(product1);
        when(productServiceClient.getProductDetails(PRODUCT_2_ID)).thenReturn(product2);

        WishlistResponse response = wishlistService.getCustomerWishlist(CUSTOMER_A_ID);

        assertNotNull(response);
        assertEquals(2, response.getTotalItems());
        assertEquals(2, response.getItems().size());
        assertEquals("Fresh Tomato", response.getItems().get(0).getProductName());
        assertEquals(BigDecimal.valueOf(40.00), response.getItems().get(0).getPrice());
        assertEquals("Red Onion", response.getItems().get(1).getProductName());
        assertEquals(BigDecimal.valueOf(35.00), response.getItems().get(1).getPrice());
    }

    @Test
    @DisplayName("Should return empty wishlist when customer has no saved items")
    void testGetCustomerWishlist_Empty() {
        when(wishlistRepository.findByCustomerPublicIdWithItems(CUSTOMER_A_ID))
                .thenReturn(Optional.of(sampleWishlist));

        WishlistResponse response = wishlistService.getCustomerWishlist(CUSTOMER_A_ID);

        assertNotNull(response);
        assertEquals(0, response.getTotalItems());
        assertTrue(response.getItems().isEmpty());
    }

    @Test
    @DisplayName("Should handle Product Service temporary unavailability gracefully without failing wishlist")
    void testGetCustomerWishlist_ProductServiceUnavailable_Resilient() {
        WishlistItem item1 = WishlistItem.builder()
                .id(101L)
                .wishlist(sampleWishlist)
                .productPublicId(PRODUCT_1_ID)
                .addedAt(Instant.now())
                .build();

        sampleWishlist.setItems(new ArrayList<>(List.of(item1)));
        when(wishlistRepository.findByCustomerPublicIdWithItems(CUSTOMER_A_ID))
                .thenReturn(Optional.of(sampleWishlist));

        // Downstream returns fallback placeholder
        ProductDto fallbackDto = ProductDto.builder()
                .publicId(PRODUCT_1_ID)
                .name("Product information temporarily unavailable")
                .status("UNKNOWN")
                .available(false)
                .stockStatus("UNKNOWN")
                .pricePerKg(BigDecimal.ZERO)
                .build();
        when(productServiceClient.getProductDetails(PRODUCT_1_ID)).thenReturn(fallbackDto);

        WishlistResponse response = wishlistService.getCustomerWishlist(CUSTOMER_A_ID);

        assertNotNull(response);
        assertEquals(1, response.getTotalItems());
        assertEquals(PRODUCT_1_ID, response.getItems().get(0).getProductPublicId());
        assertEquals("Product information temporarily unavailable", response.getItems().get(0).getProductName());
        assertFalse(response.getItems().get(0).getIsAvailable());
    }

    @Test
    @DisplayName("Should successfully remove product from customer's wishlist")
    void testRemoveProductFromWishlist_Success() {
        when(wishlistRepository.findByCustomerPublicId(CUSTOMER_A_ID)).thenReturn(Optional.of(sampleWishlist));
        when(wishlistItemRepository.existsByWishlistIdAndProductPublicId(sampleWishlist.getId(), PRODUCT_1_ID)).thenReturn(true);

        boolean removed = wishlistService.removeProductFromWishlist(CUSTOMER_A_ID, PRODUCT_1_ID);

        assertTrue(removed);
        verify(wishlistItemRepository, times(1)).deleteByWishlistIdAndProductPublicId(sampleWishlist.getId(), PRODUCT_1_ID);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when removing product not in customer's wishlist")
    void testRemoveProductFromWishlist_NotFound() {
        when(wishlistRepository.findByCustomerPublicId(CUSTOMER_A_ID)).thenReturn(Optional.of(sampleWishlist));
        when(wishlistItemRepository.existsByWishlistIdAndProductPublicId(sampleWishlist.getId(), PRODUCT_1_ID)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () ->
                wishlistService.removeProductFromWishlist(CUSTOMER_A_ID, PRODUCT_1_ID));

        verify(wishlistItemRepository, never()).deleteByWishlistIdAndProductPublicId(any(), any());
    }

    @Test
    @DisplayName("Should clear customer's wishlist")
    void testClearCustomerWishlist_Success() {
        when(wishlistRepository.findByCustomerPublicId(CUSTOMER_A_ID)).thenReturn(Optional.of(sampleWishlist));

        wishlistService.clearCustomerWishlist(CUSTOMER_A_ID);

        verify(wishlistItemRepository, times(1)).deleteByWishlistId(sampleWishlist.getId());
    }

    @Test
    @DisplayName("Should return accurate wishlist item count")
    void testGetWishlistCount() {
        when(wishlistItemRepository.countByCustomerPublicId(CUSTOMER_A_ID)).thenReturn(5);

        WishlistCountResponse response = wishlistService.getWishlistCount(CUSTOMER_A_ID);

        assertNotNull(response);
        assertEquals(5, response.getTotalItems());
    }

    @Test
    @DisplayName("Should check product wishlisted status accurately")
    void testIsProductWishlisted() {
        when(wishlistRepository.findByCustomerPublicId(CUSTOMER_A_ID)).thenReturn(Optional.of(sampleWishlist));
        when(wishlistItemRepository.existsByWishlistIdAndProductPublicId(sampleWishlist.getId(), PRODUCT_1_ID)).thenReturn(true);
        when(wishlistItemRepository.existsByWishlistIdAndProductPublicId(sampleWishlist.getId(), PRODUCT_2_ID)).thenReturn(false);

        WishlistStatusResponse resp1 = wishlistService.isProductWishlisted(CUSTOMER_A_ID, PRODUCT_1_ID);
        assertTrue(resp1.getIsWishlisted());

        WishlistStatusResponse resp2 = wishlistService.isProductWishlisted(CUSTOMER_A_ID, PRODUCT_2_ID);
        assertFalse(resp2.getIsWishlisted());
    }

    @Test
    @DisplayName("Should check bulk wishlist status for multiple products")
    void testCheckBulkStatus() {
        List<String> productIds = List.of(PRODUCT_1_ID, PRODUCT_2_ID);
        when(wishlistItemRepository.findExistingProductPublicIdsByCustomerAndProducts(CUSTOMER_A_ID, productIds))
                .thenReturn(List.of(PRODUCT_1_ID));

        WishlistBulkStatusResponse response = wishlistService.checkBulkStatus(CUSTOMER_A_ID, productIds);

        assertNotNull(response);
        assertEquals(CUSTOMER_A_ID, response.getCustomerPublicId());
        assertTrue(response.getStatusMap().get(PRODUCT_1_ID));
        assertFalse(response.getStatusMap().get(PRODUCT_2_ID));
    }

    @Test
    @DisplayName("Should validate customer data isolation")
    void testCustomerDataIsolation() {
        // Customer A and Customer B have independent wishlists
        Wishlist wishlistA = Wishlist.builder().id(1L).customerPublicId(CUSTOMER_A_ID).build();
        Wishlist wishlistB = Wishlist.builder().id(2L).customerPublicId(CUSTOMER_B_ID).build();

        when(wishlistRepository.findByCustomerPublicId(CUSTOMER_A_ID)).thenReturn(Optional.of(wishlistA));
        when(wishlistRepository.findByCustomerPublicId(CUSTOMER_B_ID)).thenReturn(Optional.of(wishlistB));

        when(wishlistItemRepository.existsByWishlistIdAndProductPublicId(1L, PRODUCT_1_ID)).thenReturn(true);
        when(wishlistItemRepository.existsByWishlistIdAndProductPublicId(2L, PRODUCT_1_ID)).thenReturn(false);

        WishlistStatusResponse statusA = wishlistService.isProductWishlisted(CUSTOMER_A_ID, PRODUCT_1_ID);
        WishlistStatusResponse statusB = wishlistService.isProductWishlisted(CUSTOMER_B_ID, PRODUCT_1_ID);

        assertTrue(statusA.getIsWishlisted());
        assertFalse(statusB.getIsWishlisted());
    }
}
