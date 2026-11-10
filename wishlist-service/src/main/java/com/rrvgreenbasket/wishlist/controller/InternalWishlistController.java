package com.rrvgreenbasket.wishlist.controller;

import com.rrvgreenbasket.wishlist.dto.ApiResponse;
import com.rrvgreenbasket.wishlist.dto.response.WishlistCountResponse;
import com.rrvgreenbasket.wishlist.dto.response.WishlistResponse;
import com.rrvgreenbasket.wishlist.dto.response.WishlistStatusResponse;
import com.rrvgreenbasket.wishlist.service.WishlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/internal/wishlist")
@RequiredArgsConstructor
@Tag(name = "Internal Wishlist", description = "Service-to-service endpoints for internal microservice queries (Order Service, Cart Service, etc.)")
@PreAuthorize("hasAnyAuthority('ROLE_INTERNAL_SERVICE', 'ROLE_ADMIN')")
public class InternalWishlistController {

    private final WishlistService wishlistService;

    @Operation(summary = "Get complete customer wishlist (Internal S2S)")
    @GetMapping(value = "/{customerPublicId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<WishlistResponse>> getInternalWishlist(
            @Parameter(description = "Customer public UUID", required = true)
            @PathVariable String customerPublicId) {

        log.debug("S2S: Querying wishlist for customer {}", customerPublicId);
        WishlistResponse response = wishlistService.getCustomerWishlist(customerPublicId);
        return ResponseEntity.ok(ApiResponse.success("Internal wishlist retrieved successfully", response));
    }

    @Operation(summary = "Get customer wishlist item count (Internal S2S)")
    @GetMapping(value = "/{customerPublicId}/count", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<WishlistCountResponse>> getInternalWishlistCount(
            @Parameter(description = "Customer public UUID", required = true)
            @PathVariable String customerPublicId) {

        WishlistCountResponse response = wishlistService.getWishlistCount(customerPublicId);
        return ResponseEntity.ok(ApiResponse.success("Internal wishlist count retrieved successfully", response));
    }

    @Operation(summary = "Check product wishlisted status for a customer (Internal S2S)")
    @GetMapping(value = "/{customerPublicId}/exists/{productPublicId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<WishlistStatusResponse>> checkInternalProductWishlisted(
            @Parameter(description = "Customer public UUID", required = true)
            @PathVariable String customerPublicId,
            @Parameter(description = "Product public UUID", required = true)
            @PathVariable String productPublicId) {

        WishlistStatusResponse response = wishlistService.isProductWishlisted(customerPublicId, productPublicId);
        return ResponseEntity.ok(ApiResponse.success("Internal wishlist status checked successfully", response));
    }
}
