package com.rrvgreenbasket.product.service;

import com.rrvgreenbasket.product.dto.request.CreateProductRequest;
import com.rrvgreenbasket.product.dto.request.PatchProductRequest;
import com.rrvgreenbasket.product.dto.request.UpdateProductRequest;
import com.rrvgreenbasket.product.dto.response.*;
import com.rrvgreenbasket.product.enums.ProductStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

public interface ProductService {

    // Admin Operations
    ProductDetailResponse createProduct(CreateProductRequest request, List<MultipartFile> images);

    default ProductDetailResponse createProduct(CreateProductRequest request) {
        return createProduct(request, Collections.emptyList());
    }

    PageResponse<ProductSummaryResponse> getAdminProducts(
            String categorySlug,
            String search,
            Boolean available,
            ProductStatus status,
            Pageable pageable
    );

    ProductDetailResponse getAdminProductByPublicId(String publicId);

    ProductDetailResponse updateProduct(String publicId, UpdateProductRequest request, List<MultipartFile> images, Boolean replaceImages);

    default ProductDetailResponse updateProduct(String publicId, UpdateProductRequest request) {
        return updateProduct(publicId, request, null, false);
    }

    ProductDetailResponse patchProduct(String publicId, PatchProductRequest request);

    void deleteProduct(String publicId);

    ProductDetailResponse updateAvailability(String publicId, boolean adminEnabled);

    ProductDetailResponse updateStatus(String publicId, ProductStatus status);

    void deleteProductImage(String productPublicId, String imagePublicId);

    ProductDetailResponse setPrimaryImage(String productPublicId, String imagePublicId);

    // Customer Catalog Operations (No Pagination, strictly available & active)
    List<ProductSummaryResponse> getCustomerCatalog();

    ProductDetailResponse getCustomerProductByPublicId(String publicId);

    List<ProductSummaryResponse> getCustomerProductsByCategorySlug(String categorySlug);

    List<ProductSummaryResponse> getCustomerFeaturedProducts();

    ProductAvailabilityResponse checkProductAvailability(String publicId, BigDecimal requestedQuantityKg);
    void permanentlyDeleteProduct(String publicId);

}
