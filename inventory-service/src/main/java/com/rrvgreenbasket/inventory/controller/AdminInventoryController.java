package com.rrvgreenbasket.inventory.controller;

import com.rrvgreenbasket.inventory.dto.request.*;
import com.rrvgreenbasket.inventory.dto.response.*;
import com.rrvgreenbasket.inventory.enums.AlertStatus;
import com.rrvgreenbasket.inventory.enums.AlertType;
import com.rrvgreenbasket.inventory.enums.MovementType;
import com.rrvgreenbasket.inventory.enums.StockStatus;
import com.rrvgreenbasket.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/admin/inventory")
@RequiredArgsConstructor
@Tag(name = "Admin Inventory Management", description = "Administrative endpoints for warehouse initial stock setup, bulk initialization, stock-in/out, adjustments, movements audit, threshold configuration, and stock alert management")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPER_ADMIN', 'ADMIN')")
public class AdminInventoryController {

    private final InventoryService inventoryService;

    // =========================================================================
    // INITIAL STOCK SETUP
    // =========================================================================

    @Operation(
            summary = "Initialize opening stock for a single product",
            description = "Admin sets opening warehouse stock for a newly registered product. Verifies unit compatibility with Product Service and logs an INITIAL_STOCK audit movement."
    )
    @PutMapping(value = "/{productPublicId}/initialize", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<InventoryDetailResponse>> initializeStock(
            @Parameter(description = "Product public UUID") @PathVariable String productPublicId,
            @Valid @RequestBody SingleProductInitialStockRequest request) {

        log.info("Admin initializing stock for product: {}", productPublicId);
        InventoryDetailResponse response = inventoryService.initializeStock(productPublicId, request);
        return ResponseEntity.ok(ApiResponse.success("Inventory initialized successfully", response));
    }

    @Operation(
            summary = "Initialize opening stock for multiple products in bulk",
            description = "Admin sets opening warehouse stock for multiple products atomically. If verification or initialization fails for any single item, the entire bulk request rolls back."
    )
    @PostMapping(value = "/initialize", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<List<InventoryDetailResponse>>> initializeMultipleProducts(
            @Valid @RequestBody BulkInitialStockRequest request) {

        log.info("Admin bulk initializing stock for {} items", request.getItems().size());
        List<InventoryDetailResponse> responses = inventoryService.initializeMultipleProducts(request);
        return ResponseEntity.ok(ApiResponse.success("Bulk inventory initialized successfully", responses));
    }

    @Operation(
            summary = "Correct initial stock before live warehouse activity",
            description = "Admin corrects mistake made in initial stock opening balance. Allowed strictly before any subsequent stock movements or customer reservations occur."
    )
    @PutMapping(value = "/{productPublicId}/correct-initial", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<InventoryDetailResponse>> correctInitialStock(
            @Parameter(description = "Product public UUID") @PathVariable String productPublicId,
            @Valid @RequestBody CorrectInitialStockRequest request) {

        log.info("Admin requesting initial stock correction for product: {}", productPublicId);
        InventoryDetailResponse response = inventoryService.correctInitialStock(productPublicId, request);
        return ResponseEntity.ok(ApiResponse.success("Initial stock corrected successfully", response));
    }

    @Operation(
            summary = "Get inventory detail for a product",
            description = "Retrieve complete current stock ledger including physical, reserved, available quantities, threshold, and status."
    )
    @GetMapping(value = "/{productPublicId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<InventoryDetailResponse>> getInventoryByProductPublicId(
            @Parameter(description = "Product public UUID") @PathVariable String productPublicId) {

        InventoryDetailResponse response = inventoryService.getInventoryByProductPublicId(productPublicId);
        return ResponseEntity.ok(ApiResponse.success("Inventory details retrieved successfully", response));
    }

    // =========================================================================
    // PHASE 6: STOCK-IN, STOCK-OUT, AND ADJUSTMENTS
    // =========================================================================

    @Operation(
            summary = "Record stock-in replenishment",
            description = "Warehouse intake of goods from suppliers or returns. Increases physical and available inventory, evaluates alert replenishment, and creates an audit movement."
    )
    @PostMapping(value = "/stock-in", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<InventoryDetailResponse>> recordStockIn(
            @Valid @RequestBody StockInRequest request) {

        log.info("Admin recording stock-in: product={}, qty={}", request.getProductPublicId(), request.getQuantity());
        InventoryDetailResponse response = inventoryService.stockIn(request);
        return ResponseEntity.ok(ApiResponse.success("Stock-in recorded successfully", response));
    }

    @Operation(
            summary = "Record stock-out reduction",
            description = "Warehouse removal of damaged goods, shrinkage, write-offs, or sample dispatches. Validates that available stock is sufficient and physical does not fall below reserved."
    )
    @PostMapping(value = "/stock-out", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<InventoryDetailResponse>> recordStockOut(
            @Valid @RequestBody StockOutRequest request) {

        log.info("Admin recording stock-out: product={}, qty={}", request.getProductPublicId(), request.getQuantity());
        InventoryDetailResponse response = inventoryService.stockOut(request);
        return ResponseEntity.ok(ApiResponse.success("Stock-out recorded successfully", response));
    }

    @Operation(
            summary = "Reconcile warehouse stock count (Adjustments)",
            description = "Adjust warehouse inventory by relative delta or absolute physical count following periodic cycle audits. Enforces physical >= reserved quantity."
    )
    @PostMapping(value = "/adjustments", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<InventoryDetailResponse>> recordAdjustment(
            @Valid @RequestBody StockAdjustmentRequest request) {

        log.info("Admin recording stock adjustment for product: {}", request.getProductPublicId());
        InventoryDetailResponse response = inventoryService.adjustStock(request);
        return ResponseEntity.ok(ApiResponse.success("Stock adjusted successfully", response));
    }

    // =========================================================================
    // PHASE 7: INVENTORY LISTING, MOVEMENTS AUDIT, ALERTS, THRESHOLD
    // =========================================================================

    @Operation(
            summary = "List inventory items with filtering and pagination",
            description = "Filter inventory by product public ID, measurement unit, stock status, or initialization status with pagination and sorting."
    )
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Page<InventoryDetailResponse>>> listInventory(
            @Parameter(description = "Optional filter by product public ID") @RequestParam(required = false) String productPublicId,
            @Parameter(description = "Optional filter by unit of measure") @RequestParam(required = false) String unitOfMeasure,
            @Parameter(description = "Optional filter by stock status (IN_STOCK, LOW_STOCK, OUT_OF_STOCK)") @RequestParam(required = false) StockStatus stockStatus,
            @Parameter(description = "Optional filter by initialization state") @RequestParam(required = false) Boolean initialized,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<InventoryDetailResponse> page = inventoryService.getInventoryList(productPublicId, unitOfMeasure, stockStatus, initialized, pageable);
        return ResponseEntity.ok(ApiResponse.success("Inventory list retrieved successfully", page));
    }

    @Operation(
            summary = "Query stock movement audit history",
            description = "Retrieve complete ledger of all stock-in, stock-out, adjustments, reservations, and initial setup movements."
    )
    @GetMapping(value = "/movements", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Page<StockMovementResponse>>> listMovements(
            @Parameter(description = "Optional filter by product public ID") @RequestParam(required = false) String productPublicId,
            @Parameter(description = "Optional filter by movement type") @RequestParam(required = false) MovementType movementType,
            @Parameter(description = "Optional filter by reference ID") @RequestParam(required = false) String referenceId,
            @Parameter(description = "Filter movements on or after this timestamp (ISO-8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @Parameter(description = "Filter movements on or before this timestamp (ISO-8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<StockMovementResponse> page = inventoryService.getMovements(productPublicId, movementType, referenceId, startDate, endDate, pageable);
        return ResponseEntity.ok(ApiResponse.success("Stock movements retrieved successfully", page));
    }

    @Operation(
            summary = "Query stock alerts",
            description = "Retrieve low-stock and out-of-stock warehouse alerts with status filtering (ACTIVE, ACKNOWLEDGED, RESOLVED)."
    )
    @GetMapping(value = "/alerts", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Page<StockAlertResponse>>> listAlerts(
            @Parameter(description = "Optional filter by product public ID") @RequestParam(required = false) String productPublicId,
            @Parameter(description = "Optional filter by alert type (LOW_STOCK, OUT_OF_STOCK)") @RequestParam(required = false) AlertType alertType,
            @Parameter(description = "Optional filter by alert status (ACTIVE, ACKNOWLEDGED, RESOLVED)") @RequestParam(required = false) AlertStatus alertStatus,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<StockAlertResponse> page = inventoryService.getAlerts(productPublicId, alertType, alertStatus, pageable);
        return ResponseEntity.ok(ApiResponse.success("Stock alerts retrieved successfully", page));
    }

    @Operation(
            summary = "Update low stock threshold for a product",
            description = "Admin updates the minimum stock threshold that triggers a LOW_STOCK warehouse alert."
    )
    @PatchMapping(value = "/{productPublicId}/threshold", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<InventoryDetailResponse>> updateThreshold(
            @Parameter(description = "Product public UUID") @PathVariable String productPublicId,
            @Valid @RequestBody UpdateThresholdRequest request) {

        log.info("Admin updating threshold for product: {}", productPublicId);
        InventoryDetailResponse response = inventoryService.updateThreshold(productPublicId, request);
        return ResponseEntity.ok(ApiResponse.success("Low stock threshold updated successfully", response));
    }

    @Operation(
            summary = "Acknowledge an active stock alert",
            description = "Admin acknowledges that a low-stock or out-of-stock alert has been noted and replenishment is planned."
    )
    @PatchMapping(value = "/alerts/{alertPublicId}/acknowledge", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<StockAlertResponse>> acknowledgeAlert(
            @Parameter(description = "Stock alert public UUID") @PathVariable String alertPublicId) {

        log.info("Admin acknowledging stock alert: {}", alertPublicId);
        StockAlertResponse response = inventoryService.acknowledgeAlert(alertPublicId);
        return ResponseEntity.ok(ApiResponse.success("Stock alert acknowledged successfully", response));
    }
}
