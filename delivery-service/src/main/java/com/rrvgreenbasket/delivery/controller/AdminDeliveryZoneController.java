package com.rrvgreenbasket.delivery.controller;

import com.rrvgreenbasket.delivery.dto.request.CreateDeliveryZoneRequest;
import com.rrvgreenbasket.delivery.dto.request.UpdateDeliveryZoneRequest;
import com.rrvgreenbasket.delivery.dto.request.UpdateZoneStatusRequest;
import com.rrvgreenbasket.delivery.dto.response.ApiResponse;
import com.rrvgreenbasket.delivery.dto.response.DeliveryZoneResponse;
import com.rrvgreenbasket.delivery.dto.response.PageResponse;
import com.rrvgreenbasket.delivery.security.UserPrincipal;
import com.rrvgreenbasket.delivery.service.DeliveryZoneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/admin/delivery/zones")
@RequiredArgsConstructor
@Tag(name = "Admin Delivery Zone Management", description = "Endpoints for administrators to manage polygon delivery boundaries, delivery charges, and threshold policies")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminDeliveryZoneController {

    private final DeliveryZoneService deliveryZoneService;

    @Operation(summary = "Create delivery zone", description = "Defines a new delivery area polygon with custom delivery charges and minimum order amounts")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<DeliveryZoneResponse>> createZone(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateDeliveryZoneRequest request) {

        String adminId = principal != null ? principal.getUserId() : "ADMIN";
        DeliveryZoneResponse response = deliveryZoneService.createZone(request, adminId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Delivery zone created successfully", response));
    }

    @Operation(summary = "Get all delivery zones", description = "Returns paginated list of delivery zones with optional search, status, and city filters")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<PageResponse<DeliveryZoneResponse>>> getAllZones(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String city,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "zoneName") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDirection) {

        Sort sort = sortDirection.equalsIgnoreCase("DESC") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        PageResponse<DeliveryZoneResponse> zones = deliveryZoneService.getAllZones(search, active, city, pageable);
        return ResponseEntity.ok(ApiResponse.success("Delivery zones retrieved successfully", zones));
    }

    @Operation(summary = "Get delivery zone details", description = "Retrieves full configuration and boundary GeoJSON for a specific zone")
    @GetMapping(value = "/{zonePublicId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<DeliveryZoneResponse>> getZoneById(
            @Parameter(description = "Delivery Zone public UUID") @PathVariable String zonePublicId) {

        DeliveryZoneResponse response = deliveryZoneService.getZoneById(zonePublicId);
        return ResponseEntity.ok(ApiResponse.success("Delivery zone details retrieved successfully", response));
    }

    @Operation(summary = "Update delivery zone", description = "Updates zone name, boundary polygon, delivery charges, thresholds, and estimated delivery time")
    @PutMapping(value = "/{zonePublicId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<DeliveryZoneResponse>> updateZone(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Delivery Zone public UUID") @PathVariable String zonePublicId,
            @Valid @RequestBody UpdateDeliveryZoneRequest request) {

        String adminId = principal != null ? principal.getUserId() : "ADMIN";
        DeliveryZoneResponse response = deliveryZoneService.updateZone(zonePublicId, request, adminId);
        return ResponseEntity.ok(ApiResponse.success("Delivery zone updated successfully", response));
    }

    @Operation(summary = "Update zone active status", description = "Enables or disables delivery acceptance for this zone")
    @PatchMapping(value = "/{zonePublicId}/status", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<DeliveryZoneResponse>> updateZoneStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Delivery Zone public UUID") @PathVariable String zonePublicId,
            @Valid @RequestBody UpdateZoneStatusRequest request) {

        String adminId = principal != null ? principal.getUserId() : "ADMIN";
        DeliveryZoneResponse response = deliveryZoneService.updateZoneStatus(zonePublicId, request.getActive(), adminId);
        String action = Boolean.TRUE.equals(request.getActive()) ? "activated" : "deactivated";
        return ResponseEntity.ok(ApiResponse.success("Delivery zone " + action + " successfully", response));
    }

    @Operation(summary = "Delete delivery zone", description = "Safely deactivates delivery zone without corrupting historical order references")
    @DeleteMapping(value = "/{zonePublicId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Void>> deleteZone(
            @Parameter(description = "Delivery Zone public UUID") @PathVariable String zonePublicId) {

        deliveryZoneService.deleteZone(zonePublicId);
        return ResponseEntity.ok(ApiResponse.success("Delivery zone deactivated successfully", null));
    }
}
