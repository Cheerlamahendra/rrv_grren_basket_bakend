package com.rrvgreenbasket.wishlist.service;

import com.rrvgreenbasket.wishlist.dto.response.*;

import java.util.List;

public interface WishlistService {

    /**
     * Adds a product to the customer's wishlist.
     * Idempotently handles duplicates.
     */
    WishlistItemResponse addProductToWishlist(String customerPublicId, String productPublicId);

    /**
     * Retrieves the complete customer wishlist enriched with real-time product details.
     */
    WishlistResponse getCustomerWishlist(String customerPublicId);

    /**
     * Removes a product from the customer's wishlist.
     */
    boolean removeProductFromWishlist(String customerPublicId, String productPublicId);

    /**
     * Clears all items from the customer's wishlist.
     */
    void clearCustomerWishlist(String customerPublicId);

    /**
     * Returns the total number of items in the customer's wishlist.
     */
    WishlistCountResponse getWishlistCount(String customerPublicId);

    /**
     * Checks whether a specific product is in the customer's wishlist.
     */
    WishlistStatusResponse isProductWishlisted(String customerPublicId, String productPublicId);

    /**
     * Bulk inquiry to check wishlist status for multiple products.
     */
    WishlistBulkStatusResponse checkBulkStatus(String customerPublicId, List<String> productPublicIds);
}
