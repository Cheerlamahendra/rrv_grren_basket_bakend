package com.rrvgreenbasket.inventory.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.inventory.client.ProductServiceClient;
import com.rrvgreenbasket.inventory.client.dto.ProductVerificationResponse;
import com.rrvgreenbasket.inventory.dto.request.*;
import com.rrvgreenbasket.inventory.dto.response.*;
import com.rrvgreenbasket.inventory.entity.*;
import com.rrvgreenbasket.inventory.enums.*;
import com.rrvgreenbasket.inventory.exception.*;
import com.rrvgreenbasket.inventory.repository.*;
import com.rrvgreenbasket.inventory.security.SecurityUtils;
import com.rrvgreenbasket.inventory.service.InventoryService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryItemRepository inventoryItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StockReservationRequestRepository reservationRequestRepository;
    private final StockReservationItemRepository reservationItemRepository;
    private final StockAlertRepository stockAlertRepository;
    private final InventoryEventOutboxRepository outboxRepository;
    private final ProductServiceClient productServiceClient;
    private final ObjectMapper objectMapper;

    // =========================================================================
    // PHASE 5: INITIAL STOCK INITIALIZATION
    // =========================================================================

    @Override
    @Transactional
    public InventoryDetailResponse initializeStock(String productPublicId, SingleProductInitialStockRequest request) {
        log.info("Starting initial stock initialization for product: {}", productPublicId);

        validateUuid(productPublicId, "Product public ID");
        if (request == null) {
            throw new InvalidRequestException("Initial stock request body cannot be null");
        }

        return doInitializeStock(
                productPublicId.trim(),
                request.getUnitOfMeasure(),
                request.getInitialQuantity(),
                request.getLowStockThreshold(),
                request.getReason(),
                request.getNotes()
        );
    }

    @Override
    @Transactional
    public List<InventoryDetailResponse> initializeMultipleProducts(BulkInitialStockRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new InvalidRequestException("Bulk initialization request must contain at least one item");
        }
        return initializeMultipleProducts(request.getItems());
    }

    @Override
    @Transactional
    public List<InventoryDetailResponse> initializeMultipleProducts(List<InitialStockRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new InvalidRequestException("Bulk initialization request must contain at least one item");
        }

        log.info("Starting bulk stock initialization for {} products", requests.size());

        Set<String> seenProductIds = new HashSet<>();
        for (InitialStockRequest item : requests) {
            if (item.getProductPublicId() == null || item.getProductPublicId().isBlank()) {
                throw new InvalidRequestException("Product public ID cannot be blank in bulk initialization");
            }
            String normalizedId = item.getProductPublicId().trim().toLowerCase();
            if (!seenProductIds.add(normalizedId)) {
                throw new InvalidRequestException("Duplicate product public ID in bulk initialization request: " + item.getProductPublicId());
            }
        }

        List<InventoryDetailResponse> responses = new ArrayList<>();
        for (InitialStockRequest item : requests) {
            validateUuid(item.getProductPublicId(), "Product public ID");
            InventoryDetailResponse response = doInitializeStock(
                    item.getProductPublicId().trim(),
                    item.getUnitOfMeasure(),
                    item.getInitialPhysicalQuantity(),
                    item.getLowStockThreshold(),
                    item.getReason(),
                    item.getNotes()
            );
            responses.add(response);
        }

        log.info("Bulk stock initialization completed successfully for {} products", responses.size());
        return responses;
    }

    private InventoryDetailResponse doInitializeStock(
            String productPublicId,
            String requestedUnitStr,
            BigDecimal initialQuantity,
            BigDecimal lowStockThreshold,
            String reason,
            String notes) {

        Optional<InventoryItem> existingOpt = inventoryItemRepository.findByProductPublicId(productPublicId);
        if (existingOpt.isPresent() && Boolean.TRUE.equals(existingOpt.get().getInitialized())) {
            throw new InventoryAlreadyInitializedException("Inventory has already been initialized for product: " + productPublicId);
        }

        if (initialQuantity == null) {
            throw new InvalidRequestException("Initial physical quantity is required for product: " + productPublicId);
        }
        if (initialQuantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidRequestException("Initial stock quantity cannot be negative for product: " + productPublicId);
        }
        BigDecimal scaledInitialQty = initialQuantity.setScale(3, RoundingMode.HALF_UP);

        BigDecimal scaledThreshold = (lowStockThreshold != null)
                ? lowStockThreshold.setScale(3, RoundingMode.HALF_UP)
                : new BigDecimal("10.000");
        if (scaledThreshold.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidRequestException("Low stock threshold cannot be negative for product: " + productPublicId);
        }

        UnitOfMeasure canonicalUnit;
        try {
            canonicalUnit = UnitOfMeasure.fromString(requestedUnitStr);
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("Unsupported measurement unit: " + requestedUnitStr);
        }

        ProductVerificationResponse product = productServiceClient.getProductByPublicId(productPublicId);
        if (product == null) {
            throw new ProductNotFoundException("Product not found with public ID: " + productPublicId);
        }

        if (!canonicalUnit.matches(product.getQuantityUnit())) {
            throw new UnitMismatchException(
                    "Requested measurement unit '" + requestedUnitStr + "' does not match product configured unit '"
                            + product.getQuantityUnit() + "' for product: " + productPublicId
            );
        }

        InventoryItem item = existingOpt.orElseGet(() -> InventoryItem.builder()
                .productPublicId(productPublicId)
                .build());

        item.setUnitOfMeasure(canonicalUnit);
        item.setPhysicalQuantity(scaledInitialQty);
        item.setReservedQuantity(BigDecimal.ZERO.setScale(3));
        item.setAvailableQuantity(scaledInitialQty);
        item.setLowStockThreshold(scaledThreshold);
        item.setInitialized(true);

        InventoryItem savedItem;
        try {
            savedItem = inventoryItemRepository.saveAndFlush(item);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Concurrent duplicate initialization detected for product: {}", productPublicId);
            throw new InventoryAlreadyInitializedException("Inventory already initialized for product: " + productPublicId);
        }

        String performedBy = SecurityUtils.getCurrentUser();
        StockMovement movement = StockMovement.builder()
                .productPublicId(productPublicId)
                .movementType(MovementType.INITIAL_STOCK)
                .quantity(scaledInitialQty)
                .physicalQuantityBefore(BigDecimal.ZERO.setScale(3))
                .physicalQuantityAfter(scaledInitialQty)
                .availableQuantityBefore(BigDecimal.ZERO.setScale(3))
                .availableQuantityAfter(scaledInitialQty)
                .unitOfMeasure(canonicalUnit)
                .reason(reason != null && !reason.isBlank() ? reason.trim() : "Initial warehouse stock")
                .referenceType("INITIAL_STOCK")
                .referenceId(productPublicId)
                .notes(notes)
                .performedBy(performedBy)
                .build();

        stockMovementRepository.save(movement);

        evaluateAlertLifecycle(savedItem);
        createOutboxEvent(savedItem, "STOCK_INITIALIZED");

        log.info("Successfully initialized stock for product '{}': {} {} (performedBy={})",
                productPublicId, scaledInitialQty, canonicalUnit, performedBy);

        return InventoryDetailResponse.fromEntity(savedItem);
    }

    @Override
    @Transactional
    public InventoryDetailResponse correctInitialStock(String productPublicId, CorrectInitialStockRequest request) {
        log.info("Requesting initial stock correction for product: {}", productPublicId);

        validateUuid(productPublicId, "Product public ID");
        if (request == null) {
            throw new InvalidRequestException("Correction request body cannot be null");
        }

        InventoryItem item = inventoryItemRepository.findByProductPublicIdWithLock(productPublicId.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory record not found for product: " + productPublicId));

        if (!Boolean.TRUE.equals(item.getInitialized())) {
            throw new InvalidRequestException("Cannot correct uninitialized inventory for product: " + productPublicId);
        }

        var movements = stockMovementRepository.findByProductPublicIdOrderByCreatedAtDesc(productPublicId, Pageable.unpaged());
        boolean hasSubsequentMovements = movements.getContent().stream()
                .anyMatch(m -> m.getMovementType() != MovementType.INITIAL_STOCK);
        if (hasSubsequentMovements || movements.getTotalElements() > 1) {
            throw new DuplicateResourceException(
                    "Cannot correct initial stock: subsequent stock movements already exist for product: " + productPublicId
            );
        }

        var reservations = reservationItemRepository.findByProductPublicId(productPublicId);
        if (!reservations.isEmpty() || item.getReservedQuantity().compareTo(BigDecimal.ZERO) > 0) {
            throw new DuplicateResourceException(
                    "Cannot correct initial stock: reservations or fulfillment activity exist for product: " + productPublicId
            );
        }

        if (request.getCorrectedQuantity() == null || request.getCorrectedQuantity().compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidRequestException("Corrected quantity cannot be negative");
        }

        BigDecimal oldPhysical = item.getPhysicalQuantity();
        BigDecimal oldAvailable = item.getAvailableQuantity();
        BigDecimal newQty = request.getCorrectedQuantity().setScale(3, RoundingMode.HALF_UP);

        item.setPhysicalQuantity(newQty);
        item.recalculateAvailable();
        InventoryItem savedItem = inventoryItemRepository.saveAndFlush(item);

        StockMovement correctionMovement = StockMovement.builder()
                .productPublicId(productPublicId)
                .movementType(MovementType.ADJUSTMENT)
                .quantity(newQty)
                .physicalQuantityBefore(oldPhysical)
                .physicalQuantityAfter(newQty)
                .availableQuantityBefore(oldAvailable)
                .availableQuantityAfter(newQty)
                .unitOfMeasure(savedItem.getUnitOfMeasure())
                .reason(request.getReason() != null ? request.getReason() : "Opening stock correction")
                .referenceType("INITIAL_STOCK_CORRECTION")
                .referenceId(productPublicId)
                .notes(request.getNotes())
                .performedBy(SecurityUtils.getCurrentUser())
                .build();

        stockMovementRepository.save(correctionMovement);

        evaluateAlertLifecycle(savedItem);
        createOutboxEvent(savedItem, "INITIAL_STOCK_CORRECTED");

        log.info("Successfully corrected initial stock for product '{}': from {} to {} {}",
                productPublicId, oldPhysical, newQty, savedItem.getUnitOfMeasure());

        return InventoryDetailResponse.fromEntity(savedItem);
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryDetailResponse getInventoryByProductPublicId(String productPublicId) {
        validateUuid(productPublicId, "Product public ID");
        InventoryItem item = inventoryItemRepository.findByProductPublicId(productPublicId.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product: " + productPublicId));
        return InventoryDetailResponse.fromEntity(item);
    }

    // =========================================================================
    // PHASE 6: STOCK-IN, STOCK-OUT, AND ADJUSTMENTS
    // =========================================================================

    @Override
    @Transactional
    public InventoryDetailResponse stockIn(StockInRequest request) {
        if (request == null) {
            throw new InvalidRequestException("Stock-in request cannot be null");
        }
        validateUuid(request.getProductPublicId(), "Product public ID");
        if (request.getQuantity() == null || request.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRequestException("Stock-in quantity must be greater than zero");
        }

        BigDecimal scaledQuantity = request.getQuantity().setScale(3, RoundingMode.HALF_UP);

        InventoryItem item = inventoryItemRepository.findByProductPublicIdWithLock(request.getProductPublicId().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory record not found for product: " + request.getProductPublicId()));

        if (!Boolean.TRUE.equals(item.getInitialized())) {
            throw new InvalidRequestException("Cannot perform stock-in on uninitialized product: " + request.getProductPublicId());
        }

        if (request.getUnitOfMeasure() != null && !request.getUnitOfMeasure().isBlank()) {
            UnitOfMeasure reqUnit = UnitOfMeasure.fromString(request.getUnitOfMeasure());
            if (reqUnit != item.getUnitOfMeasure()) {
                throw new UnitMismatchException(
                        "Stock-in unit '" + request.getUnitOfMeasure() + "' does not match item unit '" + item.getUnitOfMeasure() + "'"
                );
            }
        }

        BigDecimal physicalBefore = item.getPhysicalQuantity();
        BigDecimal availableBefore = item.getAvailableQuantity();
        BigDecimal physicalAfter = physicalBefore.add(scaledQuantity);

        item.setPhysicalQuantity(physicalAfter);
        item.recalculateAvailable();
        InventoryItem savedItem = inventoryItemRepository.saveAndFlush(item);

        StockMovement movement = StockMovement.builder()
                .productPublicId(item.getProductPublicId())
                .movementType(MovementType.STOCK_IN)
                .quantity(scaledQuantity)
                .physicalQuantityBefore(physicalBefore)
                .physicalQuantityAfter(savedItem.getPhysicalQuantity())
                .availableQuantityBefore(availableBefore)
                .availableQuantityAfter(savedItem.getAvailableQuantity())
                .unitOfMeasure(savedItem.getUnitOfMeasure())
                .reason(request.getReason() != null ? request.getReason().trim() : "Warehouse stock receipt")
                .referenceType(request.getReferenceType() != null ? request.getReferenceType().trim() : "PURCHASE_RECEIPT")
                .referenceId(request.getReferenceId() != null ? request.getReferenceId().trim() : item.getProductPublicId())
                .notes(request.getNotes())
                .performedBy(SecurityUtils.getCurrentUser())
                .build();

        stockMovementRepository.save(movement);

        evaluateAlertLifecycle(savedItem);
        createOutboxEvent(savedItem, "STOCK_IN");

        log.info("Stock-in recorded for product '{}': +{} {}, new available={}",
                item.getProductPublicId(), scaledQuantity, item.getUnitOfMeasure(), savedItem.getAvailableQuantity());

        return InventoryDetailResponse.fromEntity(savedItem);
    }

    @Override
    @Transactional
    public InventoryDetailResponse stockOut(StockOutRequest request) {
        if (request == null) {
            throw new InvalidRequestException("Stock-out request cannot be null");
        }
        validateUuid(request.getProductPublicId(), "Product public ID");
        if (request.getQuantity() == null || request.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRequestException("Stock-out quantity must be greater than zero");
        }

        BigDecimal scaledQuantity = request.getQuantity().setScale(3, RoundingMode.HALF_UP);

        InventoryItem item = inventoryItemRepository.findByProductPublicIdWithLock(request.getProductPublicId().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory record not found for product: " + request.getProductPublicId()));

        if (!Boolean.TRUE.equals(item.getInitialized())) {
            throw new InvalidRequestException("Cannot perform stock-out on uninitialized product: " + request.getProductPublicId());
        }

        if (request.getUnitOfMeasure() != null && !request.getUnitOfMeasure().isBlank()) {
            UnitOfMeasure reqUnit = UnitOfMeasure.fromString(request.getUnitOfMeasure());
            if (reqUnit != item.getUnitOfMeasure()) {
                throw new UnitMismatchException(
                        "Stock-out unit '" + request.getUnitOfMeasure() + "' does not match item unit '" + item.getUnitOfMeasure() + "'"
                );
            }
        }

        if (item.getAvailableQuantity().compareTo(scaledQuantity) < 0) {
            throw new InsufficientStockException(
                    "Insufficient available stock for product " + request.getProductPublicId()
                            + ": available=" + item.getAvailableQuantity() + ", requested=" + scaledQuantity
            );
        }

        BigDecimal physicalBefore = item.getPhysicalQuantity();
        BigDecimal availableBefore = item.getAvailableQuantity();
        BigDecimal physicalAfter = physicalBefore.subtract(scaledQuantity);

        if (physicalAfter.compareTo(item.getReservedQuantity()) < 0) {
            throw new InsufficientStockException(
                    "Stock-out would reduce physical stock below reserved quantity for product "
                            + request.getProductPublicId() + ": physicalAfter=" + physicalAfter + ", reserved=" + item.getReservedQuantity()
            );
        }

        item.setPhysicalQuantity(physicalAfter);
        item.recalculateAvailable();
        InventoryItem savedItem = inventoryItemRepository.saveAndFlush(item);

        StockMovement movement = StockMovement.builder()
                .productPublicId(item.getProductPublicId())
                .movementType(MovementType.STOCK_OUT)
                .quantity(scaledQuantity)
                .physicalQuantityBefore(physicalBefore)
                .physicalQuantityAfter(savedItem.getPhysicalQuantity())
                .availableQuantityBefore(availableBefore)
                .availableQuantityAfter(savedItem.getAvailableQuantity())
                .unitOfMeasure(savedItem.getUnitOfMeasure())
                .reason(request.getReason() != null ? request.getReason().trim() : "Warehouse dispatch/loss")
                .referenceType(request.getReferenceType() != null ? request.getReferenceType().trim() : "DISPATCH")
                .referenceId(request.getReferenceId() != null ? request.getReferenceId().trim() : item.getProductPublicId())
                .notes(request.getNotes())
                .performedBy(SecurityUtils.getCurrentUser())
                .build();

        stockMovementRepository.save(movement);

        evaluateAlertLifecycle(savedItem);
        createOutboxEvent(savedItem, "STOCK_OUT");

        log.info("Stock-out recorded for product '{}': -{} {}, new available={}",
                item.getProductPublicId(), scaledQuantity, item.getUnitOfMeasure(), savedItem.getAvailableQuantity());

        return InventoryDetailResponse.fromEntity(savedItem);
    }

    @Override
    @Transactional
    public InventoryDetailResponse adjustStock(StockAdjustmentRequest request) {
        if (request == null) {
            throw new InvalidRequestException("Stock adjustment request cannot be null");
        }
        validateUuid(request.getProductPublicId(), "Product public ID");

        boolean hasDelta = request.getAdjustmentQuantity() != null;
        boolean hasAbsolute = request.getNewPhysicalQuantity() != null;

        if ((!hasDelta && !hasAbsolute) || (hasDelta && hasAbsolute)) {
            throw new InvalidRequestException("Must specify either adjustmentQuantity (delta) or newPhysicalQuantity (absolute count), but not both.");
        }

        InventoryItem item = inventoryItemRepository.findByProductPublicIdWithLock(request.getProductPublicId().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory record not found for product: " + request.getProductPublicId()));

        if (!Boolean.TRUE.equals(item.getInitialized())) {
            throw new InvalidRequestException("Cannot adjust uninitialized product: " + request.getProductPublicId());
        }

        if (request.getUnitOfMeasure() != null && !request.getUnitOfMeasure().isBlank()) {
            UnitOfMeasure reqUnit = UnitOfMeasure.fromString(request.getUnitOfMeasure());
            if (reqUnit != item.getUnitOfMeasure()) {
                throw new UnitMismatchException(
                        "Adjustment unit '" + request.getUnitOfMeasure() + "' does not match item unit '" + item.getUnitOfMeasure() + "'"
                );
            }
        }

        BigDecimal physicalBefore = item.getPhysicalQuantity();
        BigDecimal availableBefore = item.getAvailableQuantity();
        BigDecimal targetPhysical;
        BigDecimal deltaQuantity;

        if (hasDelta) {
            deltaQuantity = request.getAdjustmentQuantity().setScale(3, RoundingMode.HALF_UP);
            targetPhysical = physicalBefore.add(deltaQuantity);
        } else {
            targetPhysical = request.getNewPhysicalQuantity().setScale(3, RoundingMode.HALF_UP);
            deltaQuantity = targetPhysical.subtract(physicalBefore);
        }

        if (targetPhysical.compareTo(BigDecimal.ZERO) < 0) {
            throw new InsufficientStockException("Adjusted physical quantity cannot be negative: " + targetPhysical);
        }

        if (targetPhysical.compareTo(item.getReservedQuantity()) < 0) {
            throw new InsufficientStockException(
                    "Stock adjustment would reduce physical stock (" + targetPhysical
                            + ") below active reserved quantity (" + item.getReservedQuantity() + ")"
            );
        }

        item.setPhysicalQuantity(targetPhysical);
        item.recalculateAvailable();
        InventoryItem savedItem = inventoryItemRepository.saveAndFlush(item);

        StockMovement movement = StockMovement.builder()
                .productPublicId(item.getProductPublicId())
                .movementType(MovementType.ADJUSTMENT)
                .quantity(deltaQuantity.abs())
                .physicalQuantityBefore(physicalBefore)
                .physicalQuantityAfter(targetPhysical)
                .availableQuantityBefore(availableBefore)
                .availableQuantityAfter(savedItem.getAvailableQuantity())
                .unitOfMeasure(savedItem.getUnitOfMeasure())
                .reason(request.getReason() != null ? request.getReason().trim() : "Warehouse audit reconciliation")
                .referenceType(request.getReferenceType() != null ? request.getReferenceType().trim() : "STOCK_ADJUSTMENT")
                .referenceId(request.getReferenceId() != null ? request.getReferenceId().trim() : item.getProductPublicId())
                .notes(request.getNotes())
                .performedBy(SecurityUtils.getCurrentUser())
                .build();

        stockMovementRepository.save(movement);

        evaluateAlertLifecycle(savedItem);
        createOutboxEvent(savedItem, "STOCK_ADJUSTED");

        log.info("Stock adjustment recorded for product '{}': from {} to {} {} (delta={})",
                item.getProductPublicId(), physicalBefore, targetPhysical, item.getUnitOfMeasure(), deltaQuantity);

        return InventoryDetailResponse.fromEntity(savedItem);
    }

    // =========================================================================
    // PHASE 7: ADMIN MANAGEMENT QUERIES AND THRESHOLD/ALERT UPDATES
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public Page<InventoryDetailResponse> getInventoryList(
            String productPublicId,
            String unitOfMeasure,
            StockStatus status,
            Boolean initialized,
            Pageable pageable) {

        Specification<InventoryItem> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (productPublicId != null && !productPublicId.isBlank()) {
                predicates.add(cb.equal(root.get("productPublicId"), productPublicId.trim()));
            }

            if (unitOfMeasure != null && !unitOfMeasure.isBlank()) {
                try {
                    UnitOfMeasure uom = UnitOfMeasure.fromString(unitOfMeasure);
                    predicates.add(cb.equal(root.get("unitOfMeasure"), uom));
                } catch (IllegalArgumentException ignored) {
                }
            }

            if (initialized != null) {
                predicates.add(cb.equal(root.get("initialized"), initialized));
            }

            if (status != null) {
                if (status == StockStatus.OUT_OF_STOCK) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("availableQuantity"), BigDecimal.ZERO));
                } else if (status == StockStatus.LOW_STOCK) {
                    predicates.add(cb.and(
                            cb.greaterThan(root.get("availableQuantity"), BigDecimal.ZERO),
                            cb.lessThanOrEqualTo(root.get("availableQuantity"), root.get("lowStockThreshold"))
                    ));
                } else if (status == StockStatus.IN_STOCK) {
                    predicates.add(cb.greaterThan(root.get("availableQuantity"), root.get("lowStockThreshold")));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return inventoryItemRepository.findAll(spec, pageable).map(InventoryDetailResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StockMovementResponse> getMovements(
            String productPublicId,
            MovementType movementType,
            String referenceId,
            Instant startDate,
            Instant endDate,
            Pageable pageable) {

        Specification<StockMovement> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (productPublicId != null && !productPublicId.isBlank()) {
                predicates.add(cb.equal(root.get("productPublicId"), productPublicId.trim()));
            }

            if (movementType != null) {
                predicates.add(cb.equal(root.get("movementType"), movementType));
            }

            if (referenceId != null && !referenceId.isBlank()) {
                predicates.add(cb.equal(root.get("referenceId"), referenceId.trim()));
            }

            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }

            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return stockMovementRepository.findAll(spec, pageable).map(StockMovementResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StockAlertResponse> getAlerts(
            String productPublicId,
            AlertType alertType,
            AlertStatus alertStatus,
            Pageable pageable) {

        Specification<StockAlert> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (productPublicId != null && !productPublicId.isBlank()) {
                predicates.add(cb.equal(root.get("productPublicId"), productPublicId.trim()));
            }

            if (alertType != null) {
                predicates.add(cb.equal(root.get("alertType"), alertType));
            }

            if (alertStatus != null) {
                predicates.add(cb.equal(root.get("alertStatus"), alertStatus));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return stockAlertRepository.findAll(spec, pageable).map(StockAlertResponse::fromEntity);
    }

    @Override
    @Transactional
    public InventoryDetailResponse updateThreshold(String productPublicId, UpdateThresholdRequest request) {
        validateUuid(productPublicId, "Product public ID");
        if (request == null || request.getLowStockThreshold() == null) {
            throw new InvalidRequestException("Low stock threshold is required");
        }
        if (request.getLowStockThreshold().compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidRequestException("Low stock threshold cannot be negative");
        }

        BigDecimal scaledThreshold = request.getLowStockThreshold().setScale(3, RoundingMode.HALF_UP);

        InventoryItem item = inventoryItemRepository.findByProductPublicIdWithLock(productPublicId.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory record not found for product: " + productPublicId));

        item.setLowStockThreshold(scaledThreshold);
        InventoryItem savedItem = inventoryItemRepository.saveAndFlush(item);

        evaluateAlertLifecycle(savedItem);

        log.info("Updated low-stock threshold for product '{}' to {}", productPublicId, scaledThreshold);
        return InventoryDetailResponse.fromEntity(savedItem);
    }

    @Override
    @Transactional
    public StockAlertResponse acknowledgeAlert(String alertPublicId) {
        validateUuid(alertPublicId, "Alert public ID");

        StockAlert alert = stockAlertRepository.findByPublicId(alertPublicId.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Stock alert not found: " + alertPublicId));

        if (alert.getAlertStatus() == AlertStatus.RESOLVED) {
            throw new InvalidRequestException("Cannot acknowledge an already resolved stock alert: " + alertPublicId);
        }

        if (alert.getAlertStatus() == AlertStatus.ACTIVE) {
            alert.setAlertStatus(AlertStatus.ACKNOWLEDGED);
            StockAlert savedAlert = stockAlertRepository.save(alert);
            log.info("Acknowledged stock alert '{}' for product '{}'", alertPublicId, alert.getProductPublicId());
            return StockAlertResponse.fromEntity(savedAlert);
        }

        return StockAlertResponse.fromEntity(alert);
    }

    // =========================================================================
    // PHASE 8: MULTI-PRODUCT STOCK RESERVATION
    // =========================================================================

    @Override
    @Transactional
    public ReservationResponse createReservation(StockReservationRequestDto request) {
        if (request == null) {
            throw new InvalidRequestException("Reservation request body cannot be null");
        }
        validateUuid(request.getOrderPublicId(), "Order public ID");
        if (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()) {
            throw new InvalidRequestException("Idempotency key is required");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new InvalidRequestException("Reservation must contain at least one item");
        }

        String idempotencyKey = request.getIdempotencyKey().trim();

        // 1. Idempotency Check
        Optional<StockReservationRequest> existingOpt = reservationRequestRepository.findByIdempotencyKey(idempotencyKey);
        if (existingOpt.isPresent()) {
            StockReservationRequest existing = existingOpt.get();
            if (!existing.getOrderPublicId().equals(request.getOrderPublicId().trim())) {
                throw new IdempotencyConflictException(
                        "Idempotency key '" + idempotencyKey + "' already used with different order ID: " + existing.getOrderPublicId()
                );
            }
            if (existing.getItems().size() != request.getItems().size()) {
                throw new IdempotencyConflictException("Idempotency key already used with different item count");
            }
            Map<String, BigDecimal> existingItems = existing.getItems().stream()
                    .collect(Collectors.toMap(StockReservationItem::getProductPublicId, StockReservationItem::getReservedQuantity));
            for (ReservationItemDto itemDto : request.getItems()) {
                BigDecimal existingQty = existingItems.get(itemDto.getProductPublicId().trim());
                if (existingQty == null || existingQty.compareTo(itemDto.getQuantity().setScale(3, RoundingMode.HALF_UP)) != 0) {
                    throw new IdempotencyConflictException("Idempotency key already used with different item quantities");
                }
            }
            log.info("Idempotent reservation replay returning existing reservation: {}", existing.getPublicId());
            return ReservationResponse.fromEntity(existing);
        }

        // 2. Aggregate quantities & validate items
        Map<String, BigDecimal> requestedQuantities = new LinkedHashMap<>();
        for (ReservationItemDto itemDto : request.getItems()) {
            validateUuid(itemDto.getProductPublicId(), "Product public ID");
            if (itemDto.getQuantity() == null || itemDto.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidRequestException("Reservation quantity must be greater than zero for product: " + itemDto.getProductPublicId());
            }
            BigDecimal scaledQty = itemDto.getQuantity().setScale(3, RoundingMode.HALF_UP);
            requestedQuantities.merge(itemDto.getProductPublicId().trim(), scaledQty, BigDecimal::add);
        }

        // 3. Deterministic alphabetical ordering to prevent deadlocks
        List<String> sortedProductIds = new ArrayList<>(requestedQuantities.keySet());
        Collections.sort(sortedProductIds);

        // 4. Pessimistic write lock on all items
        List<InventoryItem> lockedItems = inventoryItemRepository.findByProductPublicIdInWithLock(sortedProductIds);
        Map<String, InventoryItem> itemMap = lockedItems.stream()
                .collect(Collectors.toMap(InventoryItem::getProductPublicId, i -> i));

        // 5. Verification: exist, initialized, unit matches, sufficient available stock
        for (ReservationItemDto itemDto : request.getItems()) {
            String pId = itemDto.getProductPublicId().trim();
            InventoryItem invItem = itemMap.get(pId);
            if (invItem == null) {
                throw new ResourceNotFoundException("Inventory record not found for product: " + pId);
            }
            if (!Boolean.TRUE.equals(invItem.getInitialized())) {
                throw new InvalidRequestException("Inventory not initialized for product: " + pId);
            }
            if (itemDto.getUnitOfMeasure() != null && !itemDto.getUnitOfMeasure().isBlank()) {
                UnitOfMeasure reqUnit = UnitOfMeasure.fromString(itemDto.getUnitOfMeasure());
                if (reqUnit != invItem.getUnitOfMeasure()) {
                    throw new UnitMismatchException(
                            "Requested unit '" + itemDto.getUnitOfMeasure() + "' does not match product unit '" + invItem.getUnitOfMeasure() + "' for product: " + pId
                    );
                }
            }
        }

        for (Map.Entry<String, BigDecimal> entry : requestedQuantities.entrySet()) {
            String pId = entry.getKey();
            BigDecimal totalReq = entry.getValue();
            InventoryItem invItem = itemMap.get(pId);
            if (invItem.getAvailableQuantity().compareTo(totalReq) < 0) {
                throw new InsufficientStockException(
                        "Insufficient stock for product " + pId + ": available=" + invItem.getAvailableQuantity() + ", requested=" + totalReq
                );
            }
        }

        // 6. Build reservation parent and children
        int ttlMinutes = (request.getTtlMinutes() != null && request.getTtlMinutes() > 0) ? request.getTtlMinutes() : 15;
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(ttlMinutes));

        StockReservationRequest reservation = StockReservationRequest.builder()
                .publicId(UUID.randomUUID().toString())
                .orderPublicId(request.getOrderPublicId().trim())
                .idempotencyKey(idempotencyKey)
                .status(ReservationStatus.ACTIVE)
                .expiresAt(expiresAt)
                .build();

        for (ReservationItemDto itemDto : request.getItems()) {
            String pId = itemDto.getProductPublicId().trim();
            BigDecimal itemQty = itemDto.getQuantity().setScale(3, RoundingMode.HALF_UP);
            InventoryItem invItem = itemMap.get(pId);

            StockReservationItem resItem = StockReservationItem.builder()
                    .publicId(UUID.randomUUID().toString())
                    .orderPublicId(request.getOrderPublicId().trim())
                    .productPublicId(pId)
                    .reservedQuantity(itemQty)
                    .unitOfMeasure(invItem.getUnitOfMeasure())
                    .itemStatus(ReservationStatus.ACTIVE)
                    .build();

            reservation.addItem(resItem);
        }

        // 7. Deduct from available quantity by increasing reserved quantity
        for (Map.Entry<String, BigDecimal> entry : requestedQuantities.entrySet()) {
            InventoryItem invItem = itemMap.get(entry.getKey());
            invItem.setReservedQuantity(invItem.getReservedQuantity().add(entry.getValue()));
            invItem.recalculateAvailable();
            inventoryItemRepository.save(invItem);
            evaluateAlertLifecycle(invItem);
            createOutboxEvent(invItem, "STOCK_RESERVED");
        }

        StockReservationRequest savedReservation;
        try {
            savedReservation = reservationRequestRepository.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Concurrent duplicate reservation idempotency conflict for key: {}", idempotencyKey);
            throw new IdempotencyConflictException("Duplicate reservation attempt with key: " + idempotencyKey);
        }

        log.info("Successfully created stock reservation '{}' for order '{}' with {} items",
                savedReservation.getPublicId(), savedReservation.getOrderPublicId(), savedReservation.getItems().size());

        return ReservationResponse.fromEntity(savedReservation);
    }

    // =========================================================================
    // PHASE 9: RESERVATION CONFIRMATION AND RELEASE
    // =========================================================================

    @Override
    @Transactional
    public ReservationResponse confirmReservation(String reservationIdentifier) {
        if (reservationIdentifier == null || reservationIdentifier.isBlank()) {
            throw new InvalidRequestException("Reservation identifier is required");
        }

        StockReservationRequest reservation = findReservationWithLock(reservationIdentifier.trim());

        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            log.info("Idempotent confirm: reservation '{}' is already CONFIRMED", reservation.getPublicId());
            return ReservationResponse.fromEntity(reservation);
        }

        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new InvalidReservationStatusException(
                    "Cannot confirm reservation in status '" + reservation.getStatus() + "' for reservation: " + reservationIdentifier
            );
        }

        if (reservation.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidReservationStatusException(
                    "Cannot confirm expired reservation: " + reservationIdentifier + " (expired at " + reservation.getExpiresAt() + ")"
            );
        }

        List<String> productIds = reservation.getItems().stream()
                .map(StockReservationItem::getProductPublicId)
                .distinct()
                .sorted()
                .toList();

        List<InventoryItem> lockedItems = inventoryItemRepository.findByProductPublicIdInWithLock(productIds);
        Map<String, InventoryItem> itemMap = lockedItems.stream()
                .collect(Collectors.toMap(InventoryItem::getProductPublicId, i -> i));

        String currentUser = SecurityUtils.getCurrentUser();

        for (StockReservationItem resItem : reservation.getItems()) {
            InventoryItem invItem = itemMap.get(resItem.getProductPublicId());
            if (invItem != null) {
                BigDecimal qty = resItem.getReservedQuantity();
                BigDecimal physBefore = invItem.getPhysicalQuantity();
                BigDecimal availBefore = invItem.getAvailableQuantity();

                invItem.setPhysicalQuantity(physBefore.subtract(qty));
                invItem.setReservedQuantity(invItem.getReservedQuantity().subtract(qty));
                invItem.recalculateAvailable();

                resItem.setItemStatus(ReservationStatus.CONFIRMED);

                StockMovement movement = StockMovement.builder()
                        .productPublicId(invItem.getProductPublicId())
                        .movementType(MovementType.RESERVATION_DEDUCTION)
                        .quantity(qty)
                        .physicalQuantityBefore(physBefore)
                        .physicalQuantityAfter(invItem.getPhysicalQuantity())
                        .availableQuantityBefore(availBefore)
                        .availableQuantityAfter(invItem.getAvailableQuantity())
                        .unitOfMeasure(invItem.getUnitOfMeasure())
                        .reason("Order checkout confirmation")
                        .referenceType("ORDER")
                        .referenceId(reservation.getOrderPublicId())
                        .notes("Reservation ID: " + reservation.getPublicId())
                        .performedBy(currentUser)
                        .build();

                stockMovementRepository.save(movement);
            }
        }

        for (InventoryItem invItem : lockedItems) {
            inventoryItemRepository.save(invItem);
            evaluateAlertLifecycle(invItem);
            createOutboxEvent(invItem, "RESERVATION_CONFIRMED");
        }

        reservation.setStatus(ReservationStatus.CONFIRMED);
        StockReservationRequest savedReservation = reservationRequestRepository.saveAndFlush(reservation);

        log.info("Successfully confirmed reservation '{}' for order '{}'",
                savedReservation.getPublicId(), savedReservation.getOrderPublicId());

        return ReservationResponse.fromEntity(savedReservation);
    }

    @Override
    @Transactional
    public ReservationResponse releaseReservation(String reservationIdentifier) {
        if (reservationIdentifier == null || reservationIdentifier.isBlank()) {
            throw new InvalidRequestException("Reservation identifier is required");
        }

        StockReservationRequest reservation = findReservationWithLock(reservationIdentifier.trim());

        if (reservation.getStatus() == ReservationStatus.RELEASED) {
            log.info("Idempotent release: reservation '{}' is already RELEASED", reservation.getPublicId());
            return ReservationResponse.fromEntity(reservation);
        }

        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            throw new InvalidReservationStatusException(
                    "Cannot release already confirmed reservation: " + reservationIdentifier
            );
        }

        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new InvalidReservationStatusException(
                    "Cannot release reservation in status '" + reservation.getStatus() + "' for reservation: " + reservationIdentifier
            );
        }

        List<String> productIds = reservation.getItems().stream()
                .map(StockReservationItem::getProductPublicId)
                .distinct()
                .sorted()
                .toList();

        List<InventoryItem> lockedItems = inventoryItemRepository.findByProductPublicIdInWithLock(productIds);
        Map<String, InventoryItem> itemMap = lockedItems.stream()
                .collect(Collectors.toMap(InventoryItem::getProductPublicId, i -> i));

        for (StockReservationItem resItem : reservation.getItems()) {
            InventoryItem invItem = itemMap.get(resItem.getProductPublicId());
            if (invItem != null) {
                BigDecimal qty = resItem.getReservedQuantity();
                invItem.setReservedQuantity(invItem.getReservedQuantity().subtract(qty));
                invItem.recalculateAvailable();

                resItem.setItemStatus(ReservationStatus.RELEASED);
            }
        }

        for (InventoryItem invItem : lockedItems) {
            inventoryItemRepository.save(invItem);
            evaluateAlertLifecycle(invItem);
            createOutboxEvent(invItem, "RESERVATION_RELEASED");
        }

        reservation.setStatus(ReservationStatus.RELEASED);
        StockReservationRequest savedReservation = reservationRequestRepository.saveAndFlush(reservation);

        log.info("Successfully released reservation '{}' for order '{}'",
                savedReservation.getPublicId(), savedReservation.getOrderPublicId());

        return ReservationResponse.fromEntity(savedReservation);
    }

    private StockReservationRequest findReservationWithLock(String identifier) {
        Optional<StockReservationRequest> opt = reservationRequestRepository.findByPublicIdWithLock(identifier);
        if (opt.isEmpty()) {
            opt = reservationRequestRepository.findFirstByOrderPublicIdOrderByCreatedAtDesc(identifier);
        }
        return opt.orElseThrow(() -> new ReservationNotFoundException("Reservation not found with identifier: " + identifier));
    }

    // =========================================================================
    // PHASE 11: INVENTORY AVAILABILITY API
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public StockAvailabilityResponse getAvailability(String productPublicId, BigDecimal requestedQuantity) {
        validateUuid(productPublicId, "Product public ID");
        InventoryItem item = inventoryItemRepository.findByProductPublicId(productPublicId.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product: " + productPublicId));

        BigDecimal reqQty = (requestedQuantity != null && requestedQuantity.compareTo(BigDecimal.ZERO) > 0)
                ? requestedQuantity.setScale(3, RoundingMode.HALF_UP)
                : BigDecimal.ONE.setScale(3);

        return StockAvailabilityResponse.fromEntity(item, reqQty);
    }

    // =========================================================================
    // PHASE 12: COMPLETE STOCK ALERT LIFECYCLE
    // =========================================================================

    private void evaluateAlertLifecycle(InventoryItem item) {
        BigDecimal available = item.getAvailableQuantity();
        BigDecimal threshold = item.getLowStockThreshold() != null ? item.getLowStockThreshold() : new BigDecimal("10.000");
        String pId = item.getProductPublicId();
        UnitOfMeasure uom = item.getUnitOfMeasure();

        if (available.compareTo(BigDecimal.ZERO) <= 0) {
            // Depleted: available == 0
            resolveAlertIfExists(pId, AlertType.LOW_STOCK);
            createAlertIfNotExists(pId, AlertType.OUT_OF_STOCK, BigDecimal.ZERO.setScale(3), threshold, uom);
        } else if (available.compareTo(threshold) <= 0) {
            // Low stock: 0 < available <= threshold
            resolveAlertIfExists(pId, AlertType.OUT_OF_STOCK);
            createAlertIfNotExists(pId, AlertType.LOW_STOCK, available, threshold, uom);
        } else {
            // Replenished: available > threshold
            resolveAlertIfExists(pId, AlertType.OUT_OF_STOCK);
            resolveAlertIfExists(pId, AlertType.LOW_STOCK);
        }
    }

    private void resolveAlertIfExists(String productPublicId, AlertType type) {
        stockAlertRepository.findByProductPublicIdAndAlertTypeAndAlertStatusIn(
                productPublicId, type, List.of(AlertStatus.ACTIVE, AlertStatus.ACKNOWLEDGED)
        ).ifPresent(alert -> {
            alert.setAlertStatus(AlertStatus.RESOLVED);
            alert.setResolvedAt(Instant.now());
            stockAlertRepository.save(alert);
            log.info("Auto-resolved {} alert for product '{}'", type, productPublicId);
        });
    }

    private void createAlertIfNotExists(String productPublicId, AlertType type, BigDecimal currentQty, BigDecimal threshold, UnitOfMeasure unit) {
        var existing = stockAlertRepository.findByProductPublicIdAndAlertTypeAndAlertStatusIn(
                productPublicId, type, List.of(AlertStatus.ACTIVE, AlertStatus.ACKNOWLEDGED)
        );
        if (existing.isEmpty()) {
            StockAlert alert = StockAlert.builder()
                    .productPublicId(productPublicId)
                    .alertType(type)
                    .currentQuantity(currentQty)
                    .thresholdQuantity(threshold)
                    .unitOfMeasure(unit)
                    .alertStatus(AlertStatus.ACTIVE)
                    .build();
            try {
                stockAlertRepository.save(alert);
                log.info("Created {} alert for product '{}' (current={}, threshold={})", type, productPublicId, currentQty, threshold);
            } catch (DataIntegrityViolationException ex) {
                log.debug("Duplicate unresolved alert suppressed by database constraint for product: {}", productPublicId);
            }
        }
    }

    // =========================================================================
    // TRANSACTIONAL OUTBOX HELPER
    // =========================================================================

    private void createOutboxEvent(InventoryItem item, String eventType) {
        try {
            String eventId = UUID.randomUUID().toString();
            OutboxEventPayload payload = OutboxEventPayload.builder()
                    .eventId(eventId)
                    .productPublicId(item.getProductPublicId())
                    .eventType(eventType)
                    .unitOfMeasure(item.getUnitOfMeasure() != null ? item.getUnitOfMeasure().name() : null)
                    .physicalQuantity(item.getPhysicalQuantity())
                    .reservedQuantity(item.getReservedQuantity())
                    .availableQuantity(item.getAvailableQuantity())
                    .stockStatus(item.calculateStockStatus())
                    .timestamp(Instant.now())
                    .build();

            String jsonPayload = objectMapper.writeValueAsString(payload);

            InventoryEventOutbox outbox = InventoryEventOutbox.builder()
                    .eventId(eventId)
                    .productPublicId(item.getProductPublicId())
                    .eventType(eventType)
                    .payload(jsonPayload)
                    .eventStatus(OutboxEventStatus.PENDING)
                    .retryCount(0)
                    .maxRetries(5)
                    .build();

            outboxRepository.save(outbox);
        } catch (Exception ex) {
            log.error("Failed to construct outbox event for product: {}", item.getProductPublicId(), ex);
            throw new IllegalStateException("Failed to serialize outbox event payload", ex);
        }
    }

    private void validateUuid(String uuid, String fieldName) {
        if (uuid == null || uuid.isBlank()) {
            throw new InvalidRequestException(fieldName + " is required");
        }
        try {
            UUID.fromString(uuid.trim());
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException(fieldName + " must be a valid UUID: " + uuid);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public com.rrvgreenbasket.inventory.dto.response.ProductDeletionCheckResponse checkProductCanBeDeleted(String productPublicId) {
        if (!org.springframework.util.StringUtils.hasText(productPublicId)) {
            return com.rrvgreenbasket.inventory.dto.response.ProductDeletionCheckResponse.builder()
                    .productPublicId(productPublicId)
                    .canDelete(false)
                    .reason("Product public ID is missing")
                    .build();
        }

        String trimmedId = productPublicId.trim();
        Optional<InventoryItem> itemOpt = inventoryItemRepository.findByProductPublicId(trimmedId);
        if (itemOpt.isEmpty()) {
            return com.rrvgreenbasket.inventory.dto.response.ProductDeletionCheckResponse.builder()
                    .productPublicId(trimmedId)
                    .canDelete(true)
                    .reason("No inventory records found for product")
                    .physicalStock(BigDecimal.ZERO)
                    .reservedStock(BigDecimal.ZERO)
                    .movementCount(0L)
                    .build();
        }

        InventoryItem item = itemOpt.get();
        if (item.getPhysicalQuantity() != null && item.getPhysicalQuantity().compareTo(BigDecimal.ZERO) > 0) {
            return com.rrvgreenbasket.inventory.dto.response.ProductDeletionCheckResponse.builder()
                    .productPublicId(trimmedId)
                    .canDelete(false)
                    .reason("Cannot delete product: active physical stock remains (" + item.getPhysicalQuantity() + " " + item.getUnitOfMeasure() + ")")
                    .physicalStock(item.getPhysicalQuantity())
                    .reservedStock(item.getReservedQuantity())
                    .movementCount(stockMovementRepository.countByProductPublicId(trimmedId))
                    .build();
        }

        if (item.getReservedQuantity() != null && item.getReservedQuantity().compareTo(BigDecimal.ZERO) > 0) {
            return com.rrvgreenbasket.inventory.dto.response.ProductDeletionCheckResponse.builder()
                    .productPublicId(trimmedId)
                    .canDelete(false)
                    .reason("Cannot delete product: active reserved stock remains (" + item.getReservedQuantity() + " " + item.getUnitOfMeasure() + ")")
                    .physicalStock(item.getPhysicalQuantity())
                    .reservedStock(item.getReservedQuantity())
                    .movementCount(stockMovementRepository.countByProductPublicId(trimmedId))
                    .build();
        }

        long movementCount = stockMovementRepository.countByProductPublicId(trimmedId);
        if (movementCount > 0) {
            return com.rrvgreenbasket.inventory.dto.response.ProductDeletionCheckResponse.builder()
                    .productPublicId(trimmedId)
                    .canDelete(false)
                    .reason("Cannot delete product: historical stock movement ledger records exist (" + movementCount + " movements)")
                    .physicalStock(item.getPhysicalQuantity())
                    .reservedStock(item.getReservedQuantity())
                    .movementCount(movementCount)
                    .build();
        }

        return com.rrvgreenbasket.inventory.dto.response.ProductDeletionCheckResponse.builder()
                .productPublicId(trimmedId)
                .canDelete(true)
                .reason("Product has zero stock and no historical movement records in inventory")
                .physicalStock(item.getPhysicalQuantity())
                .reservedStock(item.getReservedQuantity())
                .movementCount(0L)
                .build();
    }
}
