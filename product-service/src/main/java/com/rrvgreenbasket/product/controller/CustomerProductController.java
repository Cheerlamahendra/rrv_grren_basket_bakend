package com.rrvgreenbasket.product.controller;

import com.rrvgreenbasket.product.dto.response.ApiResponse;
import com.rrvgreenbasket.product.dto.response.CategoryResponse;
import com.rrvgreenbasket.product.dto.response.ProductAvailabilityResponse;
import com.rrvgreenbasket.product.dto.response.ProductDetailResponse;
import com.rrvgreenbasket.product.dto.response.ProductSummaryResponse;
import com.rrvgreenbasket.product.service.CategoryService;
import com.rrvgreenbasket.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Customer Product Catalog", description = "Public endpoints for browsing categories, active products, and KG price calculations without requiring authentication")
public class CustomerProductController {

    private final ProductService productService;
    private final CategoryService categoryService;

    @Operation(summary = "Get all customer-visible available products",
               description = "Returns unpaginated list of active, admin-enabled, and in-stock products.")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<List<ProductSummaryResponse>>> getAllAvailableProducts() {
        List<ProductSummaryResponse> products = productService.getCustomerCatalog();
        return ResponseEntity.ok(ApiResponse.success("Available products retrieved successfully", products));
    }

    @Operation(summary = "Get single available product by publicId")
    @GetMapping(value = "/{publicId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getProductByPublicId(
            @PathVariable String publicId) {
        ProductDetailResponse response = productService.getCustomerProductByPublicId(publicId);
        return ResponseEntity.ok(ApiResponse.success("Product details retrieved successfully", response));
    }

    @Operation(summary = "Get all active categories for customer browsing",
               description = "Returns the active main categories in defined display order.")
    @GetMapping(value = "/categories", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getActiveCategories() {
        List<CategoryResponse> categories = categoryService.getActiveCategories();
        return ResponseEntity.ok(ApiResponse.success("Active categories retrieved successfully", categories));
    }

    @Operation(summary = "Get all available products in a specific category by categorySlug",
               description = "Returns unpaginated list in stable display order.")
    @GetMapping(value = "/category/{categorySlug}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<List<ProductSummaryResponse>>> getProductsByCategorySlug(
            @PathVariable String categorySlug) {
        log.debug("Customer browsing category: {}", categorySlug);
        List<ProductSummaryResponse> products = productService.getCustomerProductsByCategorySlug(categorySlug);
        return ResponseEntity.ok(ApiResponse.success("Products in category retrieved successfully", products));
    }

    @Operation(summary = "Get available featured products",
               description = "Returns unpaginated list of featured products that are active and in stock.")
    @GetMapping(value = "/featured", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<List<ProductSummaryResponse>>> getFeaturedProducts() {
        List<ProductSummaryResponse> products = productService.getCustomerFeaturedProducts();
        return ResponseEntity.ok(ApiResponse.success("Featured products retrieved successfully", products));
    }

    @Operation(summary = "Check product availability and calculate price for requested KG quantity",
               description = "Computes totalPrice = pricePerKg * requestedQuantityKg and validates stock sufficiency.")
    @GetMapping(value = "/{publicId}/availability", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<ProductAvailabilityResponse>> checkAvailability(
            @PathVariable String publicId,
            @Parameter(description = "Requested quantity in KG (e.g. 1.000, 0.500)")
            @RequestParam(required = false, defaultValue = "1.000") BigDecimal quantity) {

        ProductAvailabilityResponse response = productService.checkProductAvailability(publicId, quantity);
        return ResponseEntity.ok(ApiResponse.success("Product availability calculated", response));
    }
}
