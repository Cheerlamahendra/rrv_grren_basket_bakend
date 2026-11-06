package com.rrvgreenbasket.inventory.controller;

import com.rrvgreenbasket.inventory.dto.request.StockReservationRequestDto;
import com.rrvgreenbasket.inventory.dto.response.ApiResponse;
import com.rrvgreenbasket.inventory.dto.response.ReservationResponse;
import com.rrvgreenbasket.inventory.dto.response.StockAvailabilityResponse;
import com.rrvgreenbasket.inventory.service.InventoryService;
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
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Slf4j
@RestController
@RequestMapping({"/api/internal/inventory", "/internal/inventory"})
@RequiredArgsConstructor
@Tag(name = "Internal Inventory Management", description = "Internal service-to-service endpoints for Order checkout reservations, confirmation, release, and availability queries")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPER_ADMIN', 'ROLE_INTERNAL_SERVICE', 'INTERNAL_SERVICE', 'ADMIN')")
public class InternalInventoryController {

    private final InventoryService inventoryService;

    @Operation(
            summary = "Create multi-product stock reservation",
            description = "Internal service creates atomic stock reservation during checkout. Enforces deterministic alphabetical locking, idempotency key verification, and atomic rollback if any item lacks sufficient stock."
    )
    @PostMapping(value = "/reservations", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<ReservationResponse>> createReservation(
            @Valid @RequestBody StockReservationRequestDto request) {

        log.info("Internal request to create stock reservation for order: {}, idempotencyKey: {}",
                request.getOrderPublicId(), request.getIdempotencyKey());
        ReservationResponse response = inventoryService.createReservation(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Stock reservation created successfully", response));
    }

    @Operation(
            summary = "Confirm stock reservation",
            description = "Internal service confirms reservation upon successful payment/checkout. Deducts physical and reserved quantities, creates RESERVATION_DEDUCTION movements, and updates reservation status to CONFIRMED."
    )
    @PostMapping(value = "/reservations/{reservationIdentifier}/confirm", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<ReservationResponse>> confirmReservation(
            @Parameter(description = "Reservation public UUID or Order public UUID") @PathVariable String reservationIdentifier) {

        log.info("Internal request to confirm reservation: {}", reservationIdentifier);
        ReservationResponse response = inventoryService.confirmReservation(reservationIdentifier);
        return ResponseEntity.ok(ApiResponse.success("Stock reservation confirmed successfully", response));
    }

    @Operation(
            summary = "Release stock reservation",
            description = "Internal service releases reserved stock upon checkout cancellation, payment failure, or cart abandonment. Releases reserved quantities back into available inventory."
    )
    @PostMapping(value = "/reservations/{reservationIdentifier}/release", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<ReservationResponse>> releaseReservation(
            @Parameter(description = "Reservation public UUID or Order public UUID") @PathVariable String reservationIdentifier) {

        log.info("Internal request to release reservation: {}", reservationIdentifier);
        ReservationResponse response = inventoryService.releaseReservation(reservationIdentifier);
        return ResponseEntity.ok(ApiResponse.success("Stock reservation released successfully", response));
    }

    @Operation(
            summary = "Internal query product stock availability",
            description = "Internal service checks real-time available stock and sufficiency for a given quantity."
    )
    @GetMapping(value = "/products/{productPublicId}/availability", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<StockAvailabilityResponse>> getAvailability(
            @Parameter(description = "Product public UUID") @PathVariable String productPublicId,
            @Parameter(description = "Optional quantity requested to check sufficiency") @RequestParam(required = false) BigDecimal quantity) {

        StockAvailabilityResponse response = inventoryService.getAvailability(productPublicId, quantity);
        return ResponseEntity.ok(ApiResponse.success("Product availability retrieved successfully", response));
    }

    @Operation(
            summary = "Internal check if product can be permanently deleted",
            description = "Checks if product has physical stock, active reservations, or inventory movement ledger entries"
    )
    @GetMapping(value = "/products/{productPublicId}/deletion-check", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<com.rrvgreenbasket.inventory.dto.response.ProductDeletionCheckResponse>> checkProductCanBeDeleted(
            @Parameter(description = "Product public UUID") @PathVariable String productPublicId) {

        log.info("Internal request to check product deletion safety for product: {}", productPublicId);
        com.rrvgreenbasket.inventory.dto.response.ProductDeletionCheckResponse response =
                inventoryService.checkProductCanBeDeleted(productPublicId);
        return ResponseEntity.ok(ApiResponse.success("Product deletion check completed", response));
    }
}
