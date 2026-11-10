package com.rrvgreenbasket.wishlist.client;

import com.rrvgreenbasket.wishlist.dto.client.ProductDto;

import java.util.List;
import java.util.Map;

public interface ProductServiceClient {

    /**
     * Validates that a product exists and is active/eligible to be wishlisted.
     * Throws ResourceNotFoundException if product is not found.
     * Throws InvalidRequestException if product is inactive.
     * Throws DownstreamServiceException if Product Service is unavailable.
     */
    ProductDto validateProductForWishlist(String productPublicId);

    /**
     * Retrieves product details for wishlist display.
     * Gracefully returns fallback representation if Product Service is unavailable
     * to avoid failing the entire customer wishlist view or corrupting saved data.
     */
    ProductDto getProductDetails(String productPublicId);

    /**
     * Retrieves details for multiple products.
     */
    Map<String, ProductDto> getProductsBatch(List<String> productPublicIds);
}
