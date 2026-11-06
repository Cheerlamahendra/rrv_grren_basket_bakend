package com.rrvgreenbasket.inventory.controller;

import com.rrvgreenbasket.inventory.dto.response.ApiResponse;
import com.rrvgreenbasket.inventory.dto.response.StockAvailabilityResponse;
import com.rrvgreenbasket.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Slf4j
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Tag(name = "Customer Inventory Inquiries", description = "Public catalog endpoints for customers to check product availability and stock status")
public class CustomerInventoryController {

    private final InventoryService inventoryService;

    @Operation(
            summary = "Check product stock availability",
            description = "Public endpoint for customers and storefront browsing to check whether a product is in stock and whether the requested quantity can be fulfilled."
    )
    @GetMapping(value = "/products/{productPublicId}/availability", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<StockAvailabilityResponse>> getProductAvailability(
            @Parameter(description = "Product public UUID") @PathVariable String productPublicId,
            @Parameter(description = "Optional quantity requested to verify fulfillment sufficiency") @RequestParam(required = false) BigDecimal quantity) {

        log.debug("Customer checking availability for product: {}, qty={}", productPublicId, quantity);
        StockAvailabilityResponse response = inventoryService.getAvailability(productPublicId, quantity);
        return ResponseEntity.ok(ApiResponse.success("Product availability retrieved successfully", response));
    }
}
