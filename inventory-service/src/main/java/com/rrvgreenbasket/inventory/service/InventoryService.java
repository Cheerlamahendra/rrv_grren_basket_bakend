package com.rrvgreenbasket.inventory.service;

import com.rrvgreenbasket.inventory.dto.request.*;
import com.rrvgreenbasket.inventory.dto.response.*;
import com.rrvgreenbasket.inventory.enums.AlertStatus;
import com.rrvgreenbasket.inventory.enums.AlertType;
import com.rrvgreenbasket.inventory.enums.MovementType;
import com.rrvgreenbasket.inventory.enums.StockStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface InventoryService {

    InventoryDetailResponse initializeStock(String productPublicId, SingleProductInitialStockRequest request);

    List<InventoryDetailResponse> initializeMultipleProducts(BulkInitialStockRequest request);

    List<InventoryDetailResponse> initializeMultipleProducts(List<InitialStockRequest> requests);

    InventoryDetailResponse correctInitialStock(String productPublicId, CorrectInitialStockRequest request);

    InventoryDetailResponse getInventoryByProductPublicId(String productPublicId);

    // Phase 6: Stock-in, stock-out, and adjustments
    InventoryDetailResponse stockIn(StockInRequest request);

    InventoryDetailResponse stockOut(StockOutRequest request);

    InventoryDetailResponse adjustStock(StockAdjustmentRequest request);

    // Phase 7: Admin query and threshold / alert management
    Page<InventoryDetailResponse> getInventoryList(
            String productPublicId,
            String unitOfMeasure,
            StockStatus status,
            Boolean initialized,
            Pageable pageable
    );

    Page<StockMovementResponse> getMovements(
            String productPublicId,
            MovementType movementType,
            String referenceId,
            Instant startDate,
            Instant endDate,
            Pageable pageable
    );

    Page<StockAlertResponse> getAlerts(
            String productPublicId,
            AlertType alertType,
            AlertStatus alertStatus,
            Pageable pageable
    );

    InventoryDetailResponse updateThreshold(String productPublicId, UpdateThresholdRequest request);

    StockAlertResponse acknowledgeAlert(String alertPublicId);

    // Phase 8: Multi-product stock reservations
    ReservationResponse createReservation(StockReservationRequestDto request);

    // Phase 9: Reservation confirmation and release
    ReservationResponse confirmReservation(String reservationIdentifier);

    ReservationResponse releaseReservation(String reservationIdentifier);

    // Phase 11: Stock availability inquiry
    StockAvailabilityResponse getAvailability(String productPublicId, BigDecimal requestedQuantity);

    // Product Deletion Guard Check
    com.rrvgreenbasket.inventory.dto.response.ProductDeletionCheckResponse checkProductCanBeDeleted(String productPublicId);
}
