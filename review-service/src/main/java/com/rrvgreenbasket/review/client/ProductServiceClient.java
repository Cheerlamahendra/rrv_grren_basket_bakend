package com.rrvgreenbasket.review.client;

import com.rrvgreenbasket.review.dto.client.ProductDto;

public interface ProductServiceClient {

    /**
     * Validates that a product exists and is active/eligible for review.
     * Throws ResourceNotFoundException if product does not exist.
     * Throws InvalidRequestException if product is inactive or unavailable.
     * Throws DownstreamServiceException if Product Service is unavailable.
     */
    ProductDto validateProductForReview(String productPublicId);

    /**
     * Retrieves product details if available.
     */
    ProductDto getProductDetails(String productPublicId);
}
