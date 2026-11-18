package com.rrvgreenbasket.cart.controller;

import com.rrvgreenbasket.cart.dto.request.AddCartItemRequest;
import com.rrvgreenbasket.cart.dto.request.UpdateCartItemRequest;
import com.rrvgreenbasket.cart.dto.response.ApiResponse;
import com.rrvgreenbasket.cart.dto.response.CartResponse;
import com.rrvgreenbasket.cart.dto.response.CartValidationResponse;
import com.rrvgreenbasket.cart.security.UserPrincipal;
import com.rrvgreenbasket.cart.service.CartService;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@Tag(name = "Shopping Cart", description = "Endpoints for authenticated customers to manage their active shopping cart, inspect real-time prices, and validate items before checkout")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyAuthority('ROLE_CUSTOMER', 'ROLE_USER', 'ROLE_ADMIN', 'CUSTOMER')")
public class CartController {

    private final CartService cartService;

    @Operation(
            summary = "Get the authenticated customer's active cart",
            description = "Retrieves or initializes the customer's active cart, performing live price and stock refresh where downstream services are available."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cart retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Customer unauthenticated or assertion invalid", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Customer access required", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<CartResponse>> getMyCart(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        log.debug("REST request to get cart for customer: {}", userPrincipal.getUserId());
        CartResponse response = cartService.getMyCart(userPrincipal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Cart retrieved successfully", response));
    }

    @Operation(
            summary = "Add an item to the active cart",
            description = "Validates product availability and stock sufficiency, merging quantities if the product and unit are already present."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Item added to cart successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request payload or unit mismatch", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Product not found in catalog", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Product unavailable or insufficient stock", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Product or Inventory Service unavailable", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping(value = "/items", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<CartResponse>> addItem(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody AddCartItemRequest request) {

        log.info("REST: Customer {} adding product {} to cart", userPrincipal.getUserId(), request.getProductPublicId());
        CartResponse response = cartService.addItem(userPrincipal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Item added to cart successfully", response));
    }

    @Operation(
            summary = "Update cart item quantity (PATCH)",
            description = "Updates quantity for an existing cart item with validation against real-time product eligibility and inventory stock."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cart item updated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid quantity", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Cart item not found in customer cart", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Insufficient inventory stock", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PatchMapping(value = "/items/{cartItemPublicId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<CartResponse>> updateItemPatch(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "Cart Item Public UUID") @PathVariable String cartItemPublicId,
            @Valid @RequestBody UpdateCartItemRequest request) {

        log.info("REST: Customer {} updating cart item {} quantity via PATCH", userPrincipal.getUserId(), cartItemPublicId);
        CartResponse response = cartService.updateItem(userPrincipal.getUserId(), cartItemPublicId, request);
        return ResponseEntity.ok(ApiResponse.success("Cart item updated successfully", response));
    }

    @Operation(
            summary = "Update cart item quantity (PUT - backward compatible)",
            description = "Backward compatible PUT endpoint matching existing frontend integration."
    )
    @PutMapping(value = "/items/{cartItemPublicId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<CartResponse>> updateItemPut(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "Cart Item Public UUID") @PathVariable String cartItemPublicId,
            @Valid @RequestBody UpdateCartItemRequest request) {

        log.info("REST: Customer {} updating cart item {} quantity via PUT", userPrincipal.getUserId(), cartItemPublicId);
        CartResponse response = cartService.updateItem(userPrincipal.getUserId(), cartItemPublicId, request);
        return ResponseEntity.ok(ApiResponse.success("Cart item updated successfully", response));
    }

    @Operation(
            summary = "Remove an item from the active cart",
            description = "Removes the specified cart item belonging to the customer's active cart."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Item removed from cart successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Cart item not found in customer cart", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @DeleteMapping(value = "/items/{cartItemPublicId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "Cart Item Public UUID") @PathVariable String cartItemPublicId) {

        log.info("REST: Customer {} removing item {}", userPrincipal.getUserId(), cartItemPublicId);
        CartResponse response = cartService.removeItem(userPrincipal.getUserId(), cartItemPublicId);
        return ResponseEntity.ok(ApiResponse.success("Item removed from cart successfully", response));
    }

    @Operation(
            summary = "Clear all items from active cart",
            description = "Removes all items from the customer's active cart."
    )
    @DeleteMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<CartResponse>> clearCart(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        log.info("REST: Customer {} clearing entire cart", userPrincipal.getUserId());
        CartResponse response = cartService.clearCart(userPrincipal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Cart cleared successfully", response));
    }

    @Operation(
            summary = "Clear all items from active cart (backward compatible route)",
            description = "Alternative DELETE /api/cart/items route for backward compatibility."
    )
    @DeleteMapping(value = "/items", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<CartResponse>> clearCartItemsRoute(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        log.info("REST: Customer {} clearing cart via /items", userPrincipal.getUserId());
        CartResponse response = cartService.clearCart(userPrincipal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Cart cleared successfully", response));
    }

    @Operation(
            summary = "Validate cart items, prices, and stock availability before checkout",
            description = "Comprehensive pre-checkout validation inspecting real-time product eligibility, price changes, and stock sufficiency without reserving inventory."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cart validation completed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Customer unauthenticated", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping(value = "/validate", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<CartValidationResponse>> validateCart(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        log.info("REST: Customer {} validating cart", userPrincipal.getUserId());
        CartValidationResponse response = cartService.validateCart(userPrincipal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Cart validation completed", response));
    }
}
