package com.rrvgreenbasket.wishlist;

import com.rrvgreenbasket.wishlist.client.ProductServiceClient;
import com.rrvgreenbasket.wishlist.exception.InvalidRequestException;
import com.rrvgreenbasket.wishlist.repository.WishlistItemRepository;
import com.rrvgreenbasket.wishlist.repository.WishlistRepository;
import com.rrvgreenbasket.wishlist.service.impl.WishlistServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class WishlistValidationTests {

    @Mock
    private WishlistRepository wishlistRepository;

    @Mock
    private WishlistItemRepository wishlistItemRepository;

    @Mock
    private ProductServiceClient productServiceClient;

    @InjectMocks
    private WishlistServiceImpl wishlistService;

    private static final String VALID_CUSTOMER_ID = "550e8400-e29b-41d4-a716-446655440001";
    private static final String INVALID_UUID = "not-a-valid-uuid";

    @Test
    @DisplayName("Should reject invalid product UUID when adding to wishlist")
    void testAddProduct_InvalidProductUuid() {
        assertThrows(InvalidRequestException.class, () ->
                wishlistService.addProductToWishlist(VALID_CUSTOMER_ID, INVALID_UUID));
    }

    @Test
    @DisplayName("Should reject null product UUID when adding to wishlist")
    void testAddProduct_NullProductUuid() {
        assertThrows(InvalidRequestException.class, () ->
                wishlistService.addProductToWishlist(VALID_CUSTOMER_ID, null));
    }

    @Test
    @DisplayName("Should reject invalid customer UUID when adding to wishlist")
    void testAddProduct_InvalidCustomerUuid() {
        assertThrows(InvalidRequestException.class, () ->
                wishlistService.addProductToWishlist(INVALID_UUID, "660e8400-e29b-41d4-a716-446655440001"));
    }

    @Test
    @DisplayName("Should reject invalid product UUID in remove product")
    void testRemoveProduct_InvalidUuid() {
        assertThrows(InvalidRequestException.class, () ->
                wishlistService.removeProductFromWishlist(VALID_CUSTOMER_ID, INVALID_UUID));
    }

    @Test
    @DisplayName("Should reject invalid UUID in bulk check")
    void testBulkStatus_InvalidUuidInList() {
        List<String> listWithInvalid = List.of("660e8400-e29b-41d4-a716-446655440001", "invalid-id");
        assertThrows(InvalidRequestException.class, () ->
                wishlistService.checkBulkStatus(VALID_CUSTOMER_ID, listWithInvalid));
    }
}
