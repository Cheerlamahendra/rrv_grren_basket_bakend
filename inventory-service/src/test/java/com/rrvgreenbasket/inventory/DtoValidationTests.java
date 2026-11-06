package com.rrvgreenbasket.inventory;

import com.rrvgreenbasket.inventory.dto.request.*;
import com.rrvgreenbasket.inventory.dto.response.*;
import com.rrvgreenbasket.inventory.entity.InventoryItem;
import com.rrvgreenbasket.inventory.entity.StockAlert;
import com.rrvgreenbasket.inventory.entity.StockMovement;
import com.rrvgreenbasket.inventory.enums.AlertStatus;
import com.rrvgreenbasket.inventory.enums.AlertType;
import com.rrvgreenbasket.inventory.enums.MovementType;
import com.rrvgreenbasket.inventory.enums.UnitOfMeasure;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DtoValidationTests {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    @DisplayName("Verify InitialStockRequest validation rejects blank product and negative quantity")
    void testInitialStockRequestValidation() {
        InitialStockRequest invalidRequest = InitialStockRequest.builder()
                .productPublicId("")
                .unitOfMeasure("")
                .initialPhysicalQuantity(new BigDecimal("-5.000"))
                .reason("")
                .build();

        Set<ConstraintViolation<InitialStockRequest>> violations = validator.validate(invalidRequest);
        assertThat(violations).hasSize(4);

        InitialStockRequest validRequest = InitialStockRequest.builder()
                .productPublicId(UUID.randomUUID().toString())
                .unitOfMeasure("KG")
                .initialPhysicalQuantity(new BigDecimal("50.000"))
                .reason("Opening stock entry")
                .build();

        Set<ConstraintViolation<InitialStockRequest>> validViolations = validator.validate(validRequest);
        assertThat(validViolations).isEmpty();
    }

    @Test
    @DisplayName("Verify StockInRequest and StockOutRequest validation rejects zero or negative quantity")
    void testStockInAndOutValidation() {
        StockInRequest zeroIn = StockInRequest.builder()
                .productPublicId(UUID.randomUUID().toString())
                .quantity(BigDecimal.ZERO)
                .reason("GRN")
                .build();
        assertThat(validator.validate(zeroIn)).isNotEmpty();

        StockOutRequest zeroOut = StockOutRequest.builder()
                .productPublicId(UUID.randomUUID().toString())
                .quantity(new BigDecimal("-1.000"))
                .reason("Damage")
                .build();
        assertThat(validator.validate(zeroOut)).isNotEmpty();

        StockInRequest validIn = StockInRequest.builder()
                .productPublicId(UUID.randomUUID().toString())
                .quantity(new BigDecimal("25.000"))
                .reason("GRN-2026-001")
                .build();
        assertThat(validator.validate(validIn)).isEmpty();
    }

    @Test
    @DisplayName("Verify StockReservationRequestDto validates non-empty items and positive quantity")
    void testStockReservationRequestValidation() {
        StockReservationRequestDto emptyItems = StockReservationRequestDto.builder()
                .orderPublicId(UUID.randomUUID().toString())
                .idempotencyKey("ORD-IDEM-001")
                .items(Collections.emptyList())
                .build();
        assertThat(validator.validate(emptyItems)).isNotEmpty();

        ReservationItemDto invalidItem = ReservationItemDto.builder()
                .productPublicId(UUID.randomUUID().toString())
                .quantity(BigDecimal.ZERO)
                .build();

        StockReservationRequestDto withInvalidItem = StockReservationRequestDto.builder()
                .orderPublicId(UUID.randomUUID().toString())
                .idempotencyKey("ORD-IDEM-002")
                .items(List.of(invalidItem))
                .build();
        assertThat(validator.validate(withInvalidItem)).isNotEmpty();

        ReservationItemDto validItem = ReservationItemDto.builder()
                .productPublicId(UUID.randomUUID().toString())
                .quantity(new BigDecimal("2.500"))
                .unitOfMeasure("KG")
                .build();

        StockReservationRequestDto validRequest = StockReservationRequestDto.builder()
                .orderPublicId(UUID.randomUUID().toString())
                .idempotencyKey("ORD-IDEM-003")
                .items(List.of(validItem))
                .build();
        assertThat(validator.validate(validRequest)).isEmpty();
    }

    @Test
    @DisplayName("Verify InventoryDetailResponse mapping from entity")
    void testInventoryDetailResponseMapping() {
        String prodId = UUID.randomUUID().toString();
        InventoryItem item = InventoryItem.builder()
                .publicId(UUID.randomUUID().toString())
                .productPublicId(prodId)
                .unitOfMeasure(UnitOfMeasure.PACK)
                .physicalQuantity(new BigDecimal("50.000"))
                .reservedQuantity(new BigDecimal("10.000"))
                .availableQuantity(new BigDecimal("40.000"))
                .lowStockThreshold(new BigDecimal("15.000"))
                .initialized(true)
                .version(1L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        InventoryDetailResponse resp = InventoryDetailResponse.fromEntity(item);

        assertThat(resp).isNotNull();
        assertThat(resp.getProductPublicId()).isEqualTo(prodId);
        assertThat(resp.getUnitOfMeasure()).isEqualTo("PACK");
        assertThat(resp.getAvailableQuantity()).isEqualByComparingTo(new BigDecimal("40.000"));
        assertThat(resp.getInitialized()).isTrue();
    }

    @Test
    @DisplayName("Verify StockAvailabilityResponse mapping from entity")
    void testStockAvailabilityResponseMapping() {
        InventoryItem item = InventoryItem.builder()
                .productPublicId(UUID.randomUUID().toString())
                .unitOfMeasure(UnitOfMeasure.GRAM)
                .physicalQuantity(new BigDecimal("500.000"))
                .reservedQuantity(new BigDecimal("100.000"))
                .availableQuantity(new BigDecimal("400.000"))
                .lowStockThreshold(new BigDecimal("50.000"))
                .build();

        StockAvailabilityResponse resp = StockAvailabilityResponse.fromEntity(item);

        assertThat(resp.isInStock()).isTrue();
        assertThat(resp.isLowStock()).isFalse();
        assertThat(resp.getAvailableQuantity()).isEqualByComparingTo(new BigDecimal("400.000"));
    }

    @Test
    @DisplayName("Verify StockMovementResponse mapping from entity")
    void testStockMovementResponseMapping() {
        StockMovement movement = StockMovement.builder()
                .publicId(UUID.randomUUID().toString())
                .productPublicId(UUID.randomUUID().toString())
                .movementType(MovementType.STOCK_IN)
                .quantity(new BigDecimal("10.000"))
                .physicalQuantityBefore(BigDecimal.ZERO)
                .physicalQuantityAfter(new BigDecimal("10.000"))
                .availableQuantityBefore(BigDecimal.ZERO)
                .availableQuantityAfter(new BigDecimal("10.000"))
                .unitOfMeasure(UnitOfMeasure.LITRE)
                .reason("Fresh restock")
                .referenceType("PO")
                .referenceId("PO-1001")
                .performedBy("admin@rrv.com")
                .createdAt(Instant.now())
                .build();

        StockMovementResponse resp = StockMovementResponse.fromEntity(movement);

        assertThat(resp.getMovementType()).isEqualTo(MovementType.STOCK_IN);
        assertThat(resp.getUnitOfMeasure()).isEqualTo("LITRE");
        assertThat(resp.getQuantity()).isEqualByComparingTo(new BigDecimal("10.000"));
    }

    @Test
    @DisplayName("Verify StockAlertResponse mapping from entity")
    void testStockAlertResponseMapping() {
        StockAlert alert = StockAlert.builder()
                .publicId(UUID.randomUUID().toString())
                .productPublicId(UUID.randomUUID().toString())
                .alertType(AlertType.LOW_STOCK)
                .currentQuantity(new BigDecimal("2.000"))
                .thresholdQuantity(new BigDecimal("5.000"))
                .unitOfMeasure(UnitOfMeasure.KG)
                .alertStatus(AlertStatus.ACTIVE)
                .createdAt(Instant.now())
                .build();

        StockAlertResponse resp = StockAlertResponse.fromEntity(alert);

        assertThat(resp.getAlertType()).isEqualTo(AlertType.LOW_STOCK);
        assertThat(resp.getAlertStatus()).isEqualTo(AlertStatus.ACTIVE);
        assertThat(resp.getCurrentQuantity()).isEqualByComparingTo(new BigDecimal("2.000"));
    }
}
