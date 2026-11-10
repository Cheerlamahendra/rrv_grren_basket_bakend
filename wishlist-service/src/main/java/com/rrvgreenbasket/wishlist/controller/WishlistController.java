package com.rrvgreenbasket.wishlist.controller;

import com.rrvgreenbasket.wishlist.dto.ApiResponse;
import com.rrvgreenbasket.wishlist.dto.request.AddToWishlistRequest;
import com.rrvgreenbasket.wishlist.dto.request.WishlistBulkStatusRequest;
import com.rrvgreenbasket.wishlist.dto.response.*;
import com.rrvgreenbasket.wishlist.exception.InvalidRequestException;
import com.rrvgreenbasket.wishlist.security.SecurityUtils;
import com.rrvgreenbasket.wishlist.service.WishlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/wishlist")
@RequiredArgsConstructor
@Tag(name = "Customer Wishlist", description = "Endpoints for authenticated customers to manage saved products, inspect real-time product details, and check wishlist status")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyAuthority('ROLE_CUSTOMER', 'ROLE_USER', 'ROLE_ADMIN')")
public class WishlistController {

    private final WishlistService wishlistService;

    @Operation(
            summary = "Add a product to the authenticated customer's wishlist",
            description = "Validates product existence and eligibility with Product Service, and saves it idempotently to the customer's wishlist."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Product added successfully or already present"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request or inactive product", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Customer unauthenticated", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Product not found in catalog", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Product Service unavailable", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping(value = "/items", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<WishlistItemResponse>> addProductToWishlist(
            @Valid @RequestBody AddToWishlistRequest request) {

        String customerPublicId = requireCurrentCustomerId();
        log.info("REST: Customer {} adding product {} to wishlist", customerPublicId, request.getProductPublicId());

        WishlistItemResponse response = wishlistService.addProductToWishlist(customerPublicId, request.getProductPublicId());
        return ResponseEntity.ok(ApiResponse.success("Product added to wishlist successfully", response));
    }

    @Operation(
            summary = "Get the complete wishlist for the authenticated customer",
            description = "Retrieves all saved products and enriches them with real-time product pricing, image, and availability from Product Service."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Wishlist retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Customer unauthenticated", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<WishlistResponse>> getCustomerWishlist() {
        String customerPublicId = requireCurrentCustomerId();
        log.debug("REST: Retrieving wishlist for customer {}", customerPublicId);

        WishlistResponse response = wishlistService.getCustomerWishlist(customerPublicId);
        return ResponseEntity.ok(ApiResponse.success("Wishlist retrieved successfully", response));
    }

    @Operation(
            summary = "Remove a specific product from customer's wishlist",
            description = "Removes the specified product from the customer's wishlist. Returns 404 if product is not present."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Product removed from wishlist successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Customer unauthenticated", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Product not in customer's wishlist", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @DeleteMapping(value = "/items/{productPublicId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Void>> removeProductFromWishlist(
            @Parameter(description = "Product UUID to remove", required = true)
            @PathVariable String productPublicId) {

        String customerPublicId = requireCurrentCustomerId();
        log.info("REST: Customer {} removing product {} from wishlist", customerPublicId, productPublicId);

        wishlistService.removeProductFromWishlist(customerPublicId, productPublicId);
        return ResponseEntity.ok(ApiResponse.success("Product removed from wishlist successfully"));
    }

    @Operation(
            summary = "Clear all products from the customer's wishlist",
            description = "Clears all saved items in the customer's wishlist. Operation is idempotent."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Wishlist cleared successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Customer unauthenticated", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @DeleteMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Void>> clearCustomerWishlist() {
        String customerPublicId = requireCurrentCustomerId();
        log.info("REST: Customer {} clearing entire wishlist", customerPublicId);

        wishlistService.clearCustomerWishlist(customerPublicId);
        return ResponseEntity.ok(ApiResponse.success("Wishlist cleared successfully"));
    }

    @Operation(
            summary = "Get the total count of items in the customer's wishlist",
            description = "Fast count query suitable for rendering badges in header navigation."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Wishlist count retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Customer unauthenticated", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping(value = "/count", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<WishlistCountResponse>> getWishlistCount() {
        String customerPublicId = requireCurrentCustomerId();
        WishlistCountResponse response = wishlistService.getWishlistCount(customerPublicId);
        return ResponseEntity.ok(ApiResponse.success("Wishlist count retrieved successfully", response));
    }

    @Operation(
            summary = "Check whether a specific product is in customer's wishlist",
            description = "Returns boolean flag indicating whether the product is wishlisted."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Wishlist status retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Customer unauthenticated", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping(value = "/items/{productPublicId}/exists", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<WishlistStatusResponse>> isProductWishlisted(
            @Parameter(description = "Product UUID to check", required = true)
            @PathVariable String productPublicId) {

        String customerPublicId = requireCurrentCustomerId();
        WishlistStatusResponse response = wishlistService.isProductWishlisted(customerPublicId, productPublicId);
        return ResponseEntity.ok(ApiResponse.success("Wishlist status retrieved successfully", response));
    }

    @Operation(
            summary = "Check wishlist status for a batch of products",
            description = "Efficiently checks wishlisted state for up to 50 products simultaneously."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Bulk status retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request or exceeded batch size", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Customer unauthenticated", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping(value = "/items/status", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<WishlistBulkStatusResponse>> checkBulkStatus(
            @Valid @RequestBody WishlistBulkStatusRequest request) {

        String customerPublicId = requireCurrentCustomerId();
        WishlistBulkStatusResponse response = wishlistService.checkBulkStatus(customerPublicId, request.getProductPublicIds());
        return ResponseEntity.ok(ApiResponse.success("Bulk wishlist status retrieved successfully", response));
    }

    private String requireCurrentCustomerId() {
        String customerId = SecurityUtils.getCurrentUserId();
        if (customerId == null || customerId.isBlank() || "SYSTEM".equalsIgnoreCase(customerId)) {
            throw new InvalidRequestException("Authenticated customer identity is missing or invalid");
        }
        return customerId;
    }
}
