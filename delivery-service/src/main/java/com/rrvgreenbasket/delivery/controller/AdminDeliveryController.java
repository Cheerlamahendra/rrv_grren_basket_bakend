package com.rrvgreenbasket.delivery.controller;

import com.rrvgreenbasket.delivery.dto.request.DeliveryPricingRequest;
import com.rrvgreenbasket.delivery.dto.request.UpdateStoreSettingsRequest;
import com.rrvgreenbasket.delivery.dto.response.ApiResponse;
import com.rrvgreenbasket.delivery.dto.response.DeliveryPricingResponse;
import com.rrvgreenbasket.delivery.dto.response.StoreLocationResponse;
import com.rrvgreenbasket.delivery.security.UserPrincipal;
import com.rrvgreenbasket.delivery.service.DeliveryPricingService;
import com.rrvgreenbasket.delivery.service.StoreLocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/delivery")
@RequiredArgsConstructor
@Tag(name = "Admin Delivery & Store Management", description = "Administrator endpoints for store location coordinates, geofence radius, and dynamic delivery pricing slabs")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminDeliveryController {

    private final StoreLocationService storeLocationService;
    private final DeliveryPricingService deliveryPricingService;

    // =========================================================================
    // Store Location & Delivery Settings
    // =========================================================================

    @Operation(summary = "Get delivery settings and store information", description = "Returns active store coordinates, max delivery distance, currency, and operational status")
    @GetMapping(value = "/settings", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<StoreLocationResponse>> getSettings() {
        StoreLocationResponse response = storeLocationService.getStoreLocationResponse();
        return ResponseEntity.ok(ApiResponse.success("Store delivery settings retrieved successfully", response));
    }

    @Operation(summary = "Update store location and delivery settings", description = "Updates primary store coordinates, maximum delivery distance, and minimum order rules")
    @PutMapping(value = "/settings", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<StoreLocationResponse>> updateSettings(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateStoreSettingsRequest request) {

        String adminId = principal != null ? principal.getUserId() : "ADMIN";
        StoreLocationResponse response = storeLocationService.updateStoreSettings(request, adminId);
        return ResponseEntity.ok(ApiResponse.success("Store delivery settings updated successfully", response));
    }

    @Operation(summary = "Toggle delivery acceptance status", description = "Enables or disables delivery acceptance for the store")
    @PatchMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<StoreLocationResponse>> toggleDeliveryStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, Boolean> statusPayload) {

        String adminId = principal != null ? principal.getUserId() : "ADMIN";
        boolean enabled = Boolean.TRUE.equals(statusPayload.get("deliveryEnabled"));
        StoreLocationResponse response = storeLocationService.updateDeliveryStatus(enabled, adminId);
        String action = enabled ? "enabled" : "disabled";
        return ResponseEntity.ok(ApiResponse.success("Delivery acceptance " + action + " successfully", response));
    }

    // =========================================================================
    // Dynamic Delivery Pricing Slabs Management
    // =========================================================================

    @Operation(summary = "Get all delivery pricing slabs", description = "Returns all configured distance-based delivery pricing slabs")
    @GetMapping(value = "/pricing", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<List<DeliveryPricingResponse>>> getPricingSlabs() {
        List<DeliveryPricingResponse> slabs = deliveryPricingService.getAllPricingSlabs();
        return ResponseEntity.ok(ApiResponse.success("Delivery pricing slabs retrieved successfully", slabs));
    }

    @Operation(summary = "Create delivery pricing slab", description = "Defines a new road-distance interval with associated delivery charge")
    @PostMapping(value = "/pricing", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<DeliveryPricingResponse>> createPricingSlab(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody DeliveryPricingRequest request) {

        String adminId = principal != null ? principal.getUserId() : "ADMIN";
        DeliveryPricingResponse response = deliveryPricingService.createPricingSlab(request, adminId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Delivery pricing slab created successfully", response));
    }

    @Operation(summary = "Update delivery pricing slab", description = "Updates distance boundaries or delivery fee for an existing pricing slab")
    @PutMapping(value = "/pricing/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<DeliveryPricingResponse>> updatePricingSlab(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Pricing slab ID") @PathVariable Long id,
            @Valid @RequestBody DeliveryPricingRequest request) {

        String adminId = principal != null ? principal.getUserId() : "ADMIN";
        DeliveryPricingResponse response = deliveryPricingService.updatePricingSlab(id, request, adminId);
        return ResponseEntity.ok(ApiResponse.success("Delivery pricing slab updated successfully", response));
    }

    @Operation(summary = "Deactivate delivery pricing slab", description = "Deactivates a pricing slab without corrupting historical references")
    @DeleteMapping(value = "/pricing/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Void>> deletePricingSlab(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Pricing slab ID") @PathVariable Long id) {

        String adminId = principal != null ? principal.getUserId() : "ADMIN";
        deliveryPricingService.deleteOrDeactivatePricingSlab(id, adminId);
        return ResponseEntity.ok(ApiResponse.success("Delivery pricing slab deactivated successfully", null));
    }
}
