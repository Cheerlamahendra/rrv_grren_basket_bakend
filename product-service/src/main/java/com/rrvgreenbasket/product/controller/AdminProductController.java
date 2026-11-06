package com.rrvgreenbasket.product.controller;

import com.rrvgreenbasket.product.dto.request.CreateProductRequest;
import com.rrvgreenbasket.product.dto.request.PatchProductRequest;
import com.rrvgreenbasket.product.dto.request.UpdateAvailabilityRequest;
import com.rrvgreenbasket.product.dto.request.UpdateProductRequest;
import com.rrvgreenbasket.product.dto.request.UpdateStatusRequest;
import com.rrvgreenbasket.product.dto.response.*;
import com.rrvgreenbasket.product.enums.ProductStatus;
import com.rrvgreenbasket.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
@Tag(name = "Admin Product Management", description = "Administrative endpoints for product CRUD, multipart multi-image creation/update, pagination, filtering, availability toggling, and S3 media management")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPER_ADMIN', 'ADMIN')")
public class AdminProductController {

    private final ProductService productService;

    @Operation(
            summary = "Create a new product with multiple images (multipart/form-data)",
            description = "Admin creates a product with pricing per KG, category association, and up to 5 images in a single multipart request."
    )
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<ProductDetailResponse>> createProduct(
            @Valid @RequestPart("product") CreateProductRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {

        log.info("Admin creating product with multipart: name={}, categoryId={}, imageCount={}",
                request.getName(), request.getEffectiveCategoryId(), images != null ? images.size() : 0);

        ProductDetailResponse response = productService.createProduct(request, images);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product created successfully", response));
    }

    @Operation(
            summary = "Create a new product (JSON body)",
            description = "Admin creates a product with pricing per KG and category association without images."
    )
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<ProductDetailResponse>> createProductJson(
            @Valid @org.springframework.web.bind.annotation.RequestBody CreateProductRequest request) {

        log.info("Admin creating product with JSON: name={}, categoryId={}",
                request.getName(), request.getEffectiveCategoryId());

        ProductDetailResponse response = productService.createProduct(request, Collections.emptyList());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product created successfully", response));
    }

    @Operation(summary = "List all products with filtering & pagination",
               description = "Returns paginated list of all products. Default page size: 10, maximum page size: 10.")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<PageResponse<ProductSummaryResponse>>> getProducts(
            @Parameter(description = "Filter by category slug") @RequestParam(required = false) String category,
            @Parameter(description = "Search by product name or description") @RequestParam(required = false) String search,
            @Parameter(description = "Filter by availability status") @RequestParam(required = false) Boolean available,
            @Parameter(description = "Filter by product status (DRAFT, ACTIVE, INACTIVE, ARCHIVED)") @RequestParam(required = false) ProductStatus status,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        log.debug("Admin querying products: category={}, search={}, available={}, status={}, page={}",
                category, search, available, status, pageable.getPageNumber());

        PageResponse<ProductSummaryResponse> response = productService.getAdminProducts(
                category, search, available, status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Products retrieved successfully", response));
    }

    @Operation(summary = "Get product details by public UUID")
    @GetMapping(value = "/{publicId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getProductById(
            @PathVariable String publicId) {
        ProductDetailResponse response = productService.getAdminProductByPublicId(publicId);
        return ResponseEntity.ok(ApiResponse.success("Product retrieved successfully", response));
    }

    @Operation(summary = "Update complete product information with optional images (multipart/form-data)")
    @PutMapping(value = "/{publicId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<ProductDetailResponse>> updateProductMultipart(
            @PathVariable String publicId,
            @Valid @RequestPart("product") UpdateProductRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @RequestParam(value = "replaceImages", required = false, defaultValue = "false") Boolean replaceImages) {

        log.info("Admin updating product with multipart: publicId={}, replaceImages={}", publicId, replaceImages);
        ProductDetailResponse response = productService.updateProduct(publicId, request, images, replaceImages);
        return ResponseEntity.ok(ApiResponse.success("Product updated successfully", response));
    }

    @Operation(summary = "Update complete product information (JSON body)")
    @PutMapping(value = "/{publicId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<ProductDetailResponse>> updateProductJson(
            @PathVariable String publicId,
            @Valid @org.springframework.web.bind.annotation.RequestBody UpdateProductRequest request) {

        log.info("Admin updating product with JSON: publicId={}", publicId);
        ProductDetailResponse response = productService.updateProduct(publicId, request, null, request.getReplaceImages());
        return ResponseEntity.ok(ApiResponse.success("Product updated successfully", response));
    }

    @Operation(summary = "Partially update product fields")
    @PatchMapping(value = "/{publicId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<ProductDetailResponse>> patchProduct(
            @PathVariable String publicId,
            @Valid @org.springframework.web.bind.annotation.RequestBody PatchProductRequest request) {
        log.info("Admin patching product: publicId={}", publicId);
        ProductDetailResponse response = productService.patchProduct(publicId, request);
        return ResponseEntity.ok(ApiResponse.success("Product patched successfully", response));
    }

    @Operation(summary = "Soft delete / archive product", description = "Marks product as ARCHIVED and disables it without destroying historical order references")
    @DeleteMapping(value = "/{publicId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable String publicId) {
        log.info("Admin deleting (archiving) product: publicId={}", publicId);
        productService.deleteProduct(publicId);
        return ResponseEntity.ok(ApiResponse.success("Product archived successfully"));
    }

    @Operation(summary = "Permanently delete product", description = "Permanently removes product and associated images after verifying no historical orders or inventory dependencies exist")
    @DeleteMapping(value = "/{publicId}/permanent", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Void>> permanentlyDeleteProduct(@PathVariable String publicId) {
        log.info("Admin permanently deleting product: publicId={}", publicId);
        productService.permanentlyDeleteProduct(publicId);
        return ResponseEntity.ok(ApiResponse.success("Product permanently deleted successfully"));
    }

    @Operation(summary = "Manually enable or disable product availability")
    @PatchMapping(value = "/{publicId}/availability", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<ProductDetailResponse>> updateAvailability(
            @PathVariable String publicId,
            @Valid @org.springframework.web.bind.annotation.RequestBody UpdateAvailabilityRequest request) {
        log.info("Admin updating availability: publicId={}, adminEnabled={}", publicId, request.getAdminEnabled());
        ProductDetailResponse response = productService.updateAvailability(publicId, request.getAdminEnabled());
        return ResponseEntity.ok(ApiResponse.success("Product availability updated successfully", response));
    }

    @Operation(summary = "Update product lifecycle status (DRAFT, ACTIVE, INACTIVE, ARCHIVED)")
    @PatchMapping(value = "/{publicId}/status", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<ProductDetailResponse>> updateStatus(
            @PathVariable String publicId,
            @Valid @org.springframework.web.bind.annotation.RequestBody UpdateStatusRequest request) {
        log.info("Admin updating status: publicId={}, status={}", publicId, request.getStatus());
        ProductDetailResponse response = productService.updateStatus(publicId, request.getStatus());
        return ResponseEntity.ok(ApiResponse.success("Product status updated successfully", response));
    }

    // =========================================================================
    // S3 IMAGE MANAGEMENT ENDPOINTS (Delete & Set Primary)
    // NOTE: Separate image upload endpoint is deliberately removed per
    // requirement: multiple images are uploaded directly with product creation/update.
    // =========================================================================

    @Operation(summary = "Delete product image from AWS S3 and database")
    @DeleteMapping(value = "/{publicId}/images/{imagePublicId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Void>> deleteImage(
            @PathVariable String publicId,
            @PathVariable String imagePublicId) {

        log.info("Admin deleting image: productPublicId={}, imagePublicId={}", publicId, imagePublicId);
        productService.deleteProductImage(publicId, imagePublicId);
        return ResponseEntity.ok(ApiResponse.success("Product image deleted successfully"));
    }

    @Operation(summary = "Set primary product image")
    @PatchMapping(value = "/{publicId}/images/{imagePublicId}/primary", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<ProductDetailResponse>> setPrimaryImage(
            @PathVariable String publicId,
            @PathVariable String imagePublicId) {

        log.info("Admin setting primary image: productPublicId={}, imagePublicId={}", publicId, imagePublicId);
        ProductDetailResponse response = productService.setPrimaryImage(publicId, imagePublicId);
        return ResponseEntity.ok(ApiResponse.success("Primary image updated successfully", response));
    }
}
