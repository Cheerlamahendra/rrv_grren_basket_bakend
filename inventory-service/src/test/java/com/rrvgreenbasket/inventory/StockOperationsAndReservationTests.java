package com.rrvgreenbasket.inventory;

import com.rrvgreenbasket.inventory.client.ProductServiceClient;
import com.rrvgreenbasket.inventory.client.dto.ProductVerificationResponse;
import com.rrvgreenbasket.inventory.dto.request.*;
import com.rrvgreenbasket.inventory.dto.response.*;
import com.rrvgreenbasket.inventory.entity.*;
import com.rrvgreenbasket.inventory.enums.*;
import com.rrvgreenbasket.inventory.exception.*;
import com.rrvgreenbasket.inventory.publisher.ProductStockEventPublisher;
import com.rrvgreenbasket.inventory.repository.*;
import com.rrvgreenbasket.inventory.scheduler.OutboxDispatcher;
import com.rrvgreenbasket.inventory.scheduler.ReservationExpiryScheduler;
import com.rrvgreenbasket.inventory.security.UserPrincipal;
import com.rrvgreenbasket.inventory.service.InventoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration;
import org.springframework.cloud.netflix.eureka.EurekaDiscoveryClientConfiguration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.cloud.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=false"
})
@EnableAutoConfiguration(exclude = {
        EurekaClientAutoConfiguration.class,
        EurekaDiscoveryClientConfiguration.class
})
@ActiveProfiles("test")
class StockOperationsAndReservationTests {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private StockReservationRequestRepository reservationRequestRepository;

    @Autowired
    private StockReservationItemRepository reservationItemRepository;

    @Autowired
    private StockAlertRepository stockAlertRepository;

    @Autowired
    private InventoryEventOutboxRepository outboxRepository;

    @Autowired
    private ReservationExpiryScheduler expiryScheduler;

    @Autowired
    private OutboxDispatcher outboxDispatcher;

    @MockBean
    private ProductServiceClient productServiceClient;

    @MockBean
    private ProductStockEventPublisher eventPublisher;

    @BeforeEach
    void setUpSecurity() {
        UserPrincipal admin = UserPrincipal.create("admin-id-456", "admin@rrvgreenbasket.com", "ADMIN", "sess-test", "ACTIVE");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private String createAndInitializeProduct(String unit, BigDecimal initialQty, BigDecimal threshold) {
        String prodId = UUID.randomUUID().toString();
        when(productServiceClient.getProductByPublicId(prodId)).thenReturn(
                ProductVerificationResponse.builder()
                        .publicId(prodId)
                        .name("Test Product " + prodId)
                        .quantityUnit(unit)
                        .status("ACTIVE")
                        .available(true)
                        .build()
        );

        inventoryService.initializeStock(prodId, SingleProductInitialStockRequest.builder()
                .unitOfMeasure(unit)
                .initialQuantity(initialQty)
                .lowStockThreshold(threshold)
                .reason("Test setup opening stock")
                .build());

        return prodId;
    }

    // =========================================================================
    // PHASE 6: STOCK-IN, STOCK-OUT, AND ADJUSTMENTS
    // =========================================================================

    @Test
    @DisplayName("Stock-In: Successfully increases physical and available stock with audit movement")
    void testStockInSuccess() {
        String prodId = createAndInitializeProduct("KG", new BigDecimal("50.000"), new BigDecimal("10.000"));

        StockInRequest request = StockInRequest.builder()
                .productPublicId(prodId)
                .quantity(new BigDecimal("25.000"))
                .unitOfMeasure("KG")
                .reason("Supplier shipment arrival")
                .referenceType("PURCHASE_RECEIPT")
                .referenceId("PO-999")
                .build();

        InventoryDetailResponse response = inventoryService.stockIn(request);

        assertThat(response.getPhysicalQuantity()).isEqualByComparingTo("75.000");
        assertThat(response.getAvailableQuantity()).isEqualByComparingTo("75.000");
        assertThat(response.getReservedQuantity()).isEqualByComparingTo("0.000");
        assertThat(response.getStockStatus()).isEqualTo(StockStatus.IN_STOCK);

        var movements = stockMovementRepository.findByProductPublicIdOrderByCreatedAtDesc(prodId, PageRequest.of(0, 10));
        assertThat(movements.getContent()).isNotEmpty();
        StockMovement latest = movements.getContent().get(0);
        assertThat(latest.getMovementType()).isEqualTo(MovementType.STOCK_IN);
        assertThat(latest.getQuantity()).isEqualByComparingTo("25.000");
        assertThat(latest.getPhysicalQuantityBefore()).isEqualByComparingTo("50.000");
        assertThat(latest.getPhysicalQuantityAfter()).isEqualByComparingTo("75.000");
    }

    @Test
    @DisplayName("Stock-In: Fails on uninitialized product or negative quantity")
    void testStockInValidations() {
        String uninitId = UUID.randomUUID().toString();

        assertThatThrownBy(() -> inventoryService.stockIn(StockInRequest.builder()
                .productPublicId(uninitId)
                .quantity(new BigDecimal("10.000"))
                .reason("Intake")
                .build()))
                .isInstanceOf(ResourceNotFoundException.class);

        String prodId = createAndInitializeProduct("PIECE", new BigDecimal("10.000"), new BigDecimal("2.000"));

        assertThatThrownBy(() -> inventoryService.stockIn(StockInRequest.builder()
                .productPublicId(prodId)
                .quantity(new BigDecimal("-5.000"))
                .reason("Negative intake")
                .build()))
                .isInstanceOf(InvalidRequestException.class);

        assertThatThrownBy(() -> inventoryService.stockIn(StockInRequest.builder()
                .productPublicId(prodId)
                .quantity(new BigDecimal("5.000"))
                .unitOfMeasure("DOZEN")
                .reason("Wrong unit")
                .build()))
                .isInstanceOf(UnitMismatchException.class);
    }

    @Test
    @DisplayName("Stock-Out: Successfully reduces stock and triggers low-stock alert when below threshold")
    void testStockOutSuccess() {
        String prodId = createAndInitializeProduct("KG", new BigDecimal("20.000"), new BigDecimal("10.000"));

        StockOutRequest request = StockOutRequest.builder()
                .productPublicId(prodId)
                .quantity(new BigDecimal("15.000"))
                .unitOfMeasure("KG")
                .reason("Damaged goods scrapped")
                .referenceType("WRITE_OFF")
                .build();

        InventoryDetailResponse response = inventoryService.stockOut(request);

        assertThat(response.getPhysicalQuantity()).isEqualByComparingTo("5.000");
        assertThat(response.getAvailableQuantity()).isEqualByComparingTo("5.000");
        assertThat(response.getStockStatus()).isEqualTo(StockStatus.LOW_STOCK);

        var alerts = stockAlertRepository.findByProductPublicIdAndAlertStatusIn(prodId, List.of(AlertStatus.ACTIVE));
        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getAlertType()).isEqualTo(AlertType.LOW_STOCK);
    }

    @Test
    @DisplayName("Stock-Out: Rejects request if requested quantity exceeds available stock")
    void testStockOutInsufficientStock() {
        String prodId = createAndInitializeProduct("KG", new BigDecimal("10.000"), new BigDecimal("2.000"));

        StockOutRequest request = StockOutRequest.builder()
                .productPublicId(prodId)
                .quantity(new BigDecimal("15.000"))
                .reason("Too much")
                .build();

        assertThatThrownBy(() -> inventoryService.stockOut(request))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Insufficient available stock");
    }

    @Test
    @DisplayName("Stock Adjustments: Relative positive and negative adjustments work properly")
    void testStockAdjustmentsDelta() {
        String prodId = createAndInitializeProduct("PACK", new BigDecimal("50.000"), new BigDecimal("10.000"));

        // 1. Positive relative adjustment
        StockAdjustmentRequest positiveAdj = StockAdjustmentRequest.builder()
                .productPublicId(prodId)
                .adjustmentQuantity(new BigDecimal("10.000"))
                .unitOfMeasure("PACK")
                .reason("Cycle recount found extra")
                .build();
        InventoryDetailResponse res1 = inventoryService.adjustStock(positiveAdj);
        assertThat(res1.getPhysicalQuantity()).isEqualByComparingTo("60.000");

        // 2. Negative relative adjustment
        StockAdjustmentRequest negativeAdj = StockAdjustmentRequest.builder()
                .productPublicId(prodId)
                .adjustmentQuantity(new BigDecimal("-20.000"))
                .unitOfMeasure("PACK")
                .reason("Cycle recount shrinkage")
                .build();
        InventoryDetailResponse res2 = inventoryService.adjustStock(negativeAdj);
        assertThat(res2.getPhysicalQuantity()).isEqualByComparingTo("40.000");
    }

    @Test
    @DisplayName("Stock Adjustments: Absolute physical count reconciliation sets exact stock")
    void testStockAdjustmentsAbsolute() {
        String prodId = createAndInitializeProduct("BUNCH", new BigDecimal("30.000"), new BigDecimal("5.000"));

        StockAdjustmentRequest absoluteAdj = StockAdjustmentRequest.builder()
                .productPublicId(prodId)
                .newPhysicalQuantity(new BigDecimal("42.000"))
                .unitOfMeasure("BUNCH")
                .reason("Quarterly physical audit reconciliation")
                .build();

        InventoryDetailResponse res = inventoryService.adjustStock(absoluteAdj);
        assertThat(res.getPhysicalQuantity()).isEqualByComparingTo("42.000");
        assertThat(res.getAvailableQuantity()).isEqualByComparingTo("42.000");
    }

    // =========================================================================
    // PHASE 8 & 9: MULTI-PRODUCT RESERVATIONS, CONFIRMATION, AND RELEASE
    // =========================================================================

    @Test
    @DisplayName("Multi-Product Reservation: Successfully reserves stock for multiple products atomically")
    void testMultiProductReservationSuccess() {
        String prod1 = createAndInitializeProduct("KG", new BigDecimal("100.000"), new BigDecimal("10.000"));
        String prod2 = createAndInitializeProduct("DOZEN", new BigDecimal("50.000"), new BigDecimal("5.000"));

        String orderId = UUID.randomUUID().toString();
        String idempKey = "idemp-" + UUID.randomUUID();

        StockReservationRequestDto request = StockReservationRequestDto.builder()
                .orderPublicId(orderId)
                .idempotencyKey(idempKey)
                .ttlMinutes(15)
                .items(List.of(
                        ReservationItemDto.builder().productPublicId(prod1).quantity(new BigDecimal("10.000")).unitOfMeasure("KG").build(),
                        ReservationItemDto.builder().productPublicId(prod2).quantity(new BigDecimal("5.000")).unitOfMeasure("DOZEN").build()
                ))
                .build();

        ReservationResponse response = inventoryService.createReservation(request);

        assertThat(response.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(response.getOrderPublicId()).isEqualTo(orderId);
        assertThat(response.getItems()).hasSize(2);

        InventoryDetailResponse item1 = inventoryService.getInventoryByProductPublicId(prod1);
        assertThat(item1.getPhysicalQuantity()).isEqualByComparingTo("100.000");
        assertThat(item1.getReservedQuantity()).isEqualByComparingTo("10.000");
        assertThat(item1.getAvailableQuantity()).isEqualByComparingTo("90.000");

        InventoryDetailResponse item2 = inventoryService.getInventoryByProductPublicId(prod2);
        assertThat(item2.getPhysicalQuantity()).isEqualByComparingTo("50.000");
        assertThat(item2.getReservedQuantity()).isEqualByComparingTo("5.000");
        assertThat(item2.getAvailableQuantity()).isEqualByComparingTo("45.000");
    }

    @Test
    @DisplayName("Multi-Product Reservation: Atomic rollback if any single product has insufficient stock")
    void testMultiProductReservationAtomicRollback() {
        String prod1 = createAndInitializeProduct("KG", new BigDecimal("100.000"), new BigDecimal("10.000"));
        String prod2 = createAndInitializeProduct("DOZEN", new BigDecimal("5.000"), new BigDecimal("2.000"));

        String orderId = UUID.randomUUID().toString();
        String idempKey = "idemp-" + UUID.randomUUID();

        StockReservationRequestDto request = StockReservationRequestDto.builder()
                .orderPublicId(orderId)
                .idempotencyKey(idempKey)
                .items(List.of(
                        ReservationItemDto.builder().productPublicId(prod1).quantity(new BigDecimal("10.000")).build(),
                        ReservationItemDto.builder().productPublicId(prod2).quantity(new BigDecimal("20.000")).build() // Exceeds available!
                ))
                .build();

        assertThatThrownBy(() -> inventoryService.createReservation(request))
                .isInstanceOf(InsufficientStockException.class);

        // Verify prod1 was NOT deducted (atomic rollback)
        InventoryDetailResponse item1 = inventoryService.getInventoryByProductPublicId(prod1);
        assertThat(item1.getReservedQuantity()).isEqualByComparingTo("0.000");
        assertThat(item1.getAvailableQuantity()).isEqualByComparingTo("100.000");
    }

    @Test
    @DisplayName("Reservation Idempotency: Replaying exact same request returns identical response without duplicating reservations")
    void testReservationIdempotencyReplay() {
        String prodId = createAndInitializeProduct("KG", new BigDecimal("50.000"), new BigDecimal("10.000"));
        String orderId = UUID.randomUUID().toString();
        String idempKey = "idemp-" + UUID.randomUUID();

        StockReservationRequestDto request = StockReservationRequestDto.builder()
                .orderPublicId(orderId)
                .idempotencyKey(idempKey)
                .items(List.of(ReservationItemDto.builder().productPublicId(prodId).quantity(new BigDecimal("8.000")).build()))
                .build();

        ReservationResponse firstResponse = inventoryService.createReservation(request);
        ReservationResponse secondResponse = inventoryService.createReservation(request);

        assertThat(firstResponse.getPublicId()).isEqualTo(secondResponse.getPublicId());

        InventoryDetailResponse item = inventoryService.getInventoryByProductPublicId(prodId);
        assertThat(item.getReservedQuantity()).isEqualByComparingTo("8.000"); // Not doubled to 16!
    }

    @Test
    @DisplayName("Reservation Idempotency: Reusing key with different order or quantities throws conflict")
    void testReservationIdempotencyConflict() {
        String prodId = createAndInitializeProduct("KG", new BigDecimal("50.000"), new BigDecimal("10.000"));
        String idempKey = "idemp-" + UUID.randomUUID();

        StockReservationRequestDto req1 = StockReservationRequestDto.builder()
                .orderPublicId(UUID.randomUUID().toString())
                .idempotencyKey(idempKey)
                .items(List.of(ReservationItemDto.builder().productPublicId(prodId).quantity(new BigDecimal("5.000")).build()))
                .build();
        inventoryService.createReservation(req1);

        StockReservationRequestDto req2 = StockReservationRequestDto.builder()
                .orderPublicId(UUID.randomUUID().toString()) // Different order!
                .idempotencyKey(idempKey)
                .items(List.of(ReservationItemDto.builder().productPublicId(prodId).quantity(new BigDecimal("5.000")).build()))
                .build();

        assertThatThrownBy(() -> inventoryService.createReservation(req2))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    @DisplayName("Reservation Confirmation: Deducts physical stock, clears reserved, logs RESERVATION_DEDUCTION")
    void testReservationConfirmationSuccess() {
        String prodId = createAndInitializeProduct("KG", new BigDecimal("50.000"), new BigDecimal("10.000"));
        String orderId = UUID.randomUUID().toString();

        ReservationResponse reservation = inventoryService.createReservation(StockReservationRequestDto.builder()
                .orderPublicId(orderId)
                .idempotencyKey("idemp-" + UUID.randomUUID())
                .items(List.of(ReservationItemDto.builder().productPublicId(prodId).quantity(new BigDecimal("12.000")).build()))
                .build());

        ReservationResponse confirmed = inventoryService.confirmReservation(reservation.getPublicId());
        assertThat(confirmed.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);

        InventoryDetailResponse item = inventoryService.getInventoryByProductPublicId(prodId);
        assertThat(item.getPhysicalQuantity()).isEqualByComparingTo("38.000"); // 50 - 12
        assertThat(item.getReservedQuantity()).isEqualByComparingTo("0.000");
        assertThat(item.getAvailableQuantity()).isEqualByComparingTo("38.000"); // Remains 38

        // Verify movement audit
        var movements = stockMovementRepository.findByProductPublicIdOrderByCreatedAtDesc(prodId, PageRequest.of(0, 10));
        assertThat(movements.getContent().stream()
                .anyMatch(m -> m.getMovementType() == MovementType.RESERVATION_DEDUCTION
                        && m.getQuantity().compareTo(new BigDecimal("12.000")) == 0)).isTrue();
    }

    @Test
    @DisplayName("Reservation Confirmation: Idempotent replay returns CONFIRMED")
    void testReservationConfirmationIdempotent() {
        String prodId = createAndInitializeProduct("KG", new BigDecimal("50.000"), new BigDecimal("10.000"));

        ReservationResponse res = inventoryService.createReservation(StockReservationRequestDto.builder()
                .orderPublicId(UUID.randomUUID().toString())
                .idempotencyKey("idemp-" + UUID.randomUUID())
                .items(List.of(ReservationItemDto.builder().productPublicId(prodId).quantity(new BigDecimal("5.000")).build()))
                .build());

        ReservationResponse first = inventoryService.confirmReservation(res.getPublicId());
        ReservationResponse second = inventoryService.confirmReservation(res.getPublicId());

        assertThat(first.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(second.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);

        InventoryDetailResponse item = inventoryService.getInventoryByProductPublicId(prodId);
        assertThat(item.getPhysicalQuantity()).isEqualByComparingTo("45.000"); // Deducted once
    }

    @Test
    @DisplayName("Reservation Release: Restores available stock without modifying physical stock")
    void testReservationReleaseSuccess() {
        String prodId = createAndInitializeProduct("KG", new BigDecimal("50.000"), new BigDecimal("10.000"));

        ReservationResponse res = inventoryService.createReservation(StockReservationRequestDto.builder()
                .orderPublicId(UUID.randomUUID().toString())
                .idempotencyKey("idemp-" + UUID.randomUUID())
                .items(List.of(ReservationItemDto.builder().productPublicId(prodId).quantity(new BigDecimal("15.000")).build()))
                .build());

        ReservationResponse released = inventoryService.releaseReservation(res.getPublicId());
        assertThat(released.getStatus()).isEqualTo(ReservationStatus.RELEASED);

        InventoryDetailResponse item = inventoryService.getInventoryByProductPublicId(prodId);
        assertThat(item.getPhysicalQuantity()).isEqualByComparingTo("50.000"); // Unchanged!
        assertThat(item.getReservedQuantity()).isEqualByComparingTo("0.000");
        assertThat(item.getAvailableQuantity()).isEqualByComparingTo("50.000"); // Restored!
    }

    @Test
    @DisplayName("Reservation Release: Releasing CONFIRMED reservation is rejected with conflict")
    void testCannotReleaseConfirmedReservation() {
        String prodId = createAndInitializeProduct("KG", new BigDecimal("50.000"), new BigDecimal("10.000"));

        ReservationResponse res = inventoryService.createReservation(StockReservationRequestDto.builder()
                .orderPublicId(UUID.randomUUID().toString())
                .idempotencyKey("idemp-" + UUID.randomUUID())
                .items(List.of(ReservationItemDto.builder().productPublicId(prodId).quantity(new BigDecimal("5.000")).build()))
                .build());

        inventoryService.confirmReservation(res.getPublicId());

        assertThatThrownBy(() -> inventoryService.releaseReservation(res.getPublicId()))
                .isInstanceOf(InvalidReservationStatusException.class)
                .hasMessageContaining("Cannot release already confirmed reservation");
    }

    // =========================================================================
    // PHASE 10: AUTOMATIC RESERVATION EXPIRATION SCHEDULER
    // =========================================================================

    @Test
    @DisplayName("Reservation Expiry: Scheduled worker releases expired reservations")
    void testReservationExpirationScheduler() {
        String prodId = createAndInitializeProduct("KG", new BigDecimal("50.000"), new BigDecimal("10.000"));

        ReservationResponse res = inventoryService.createReservation(StockReservationRequestDto.builder()
                .orderPublicId(UUID.randomUUID().toString())
                .idempotencyKey("idemp-" + UUID.randomUUID())
                .ttlMinutes(1)
                .items(List.of(ReservationItemDto.builder().productPublicId(prodId).quantity(new BigDecimal("10.000")).build()))
                .build());

        // Fast-forward expiration in DB
        StockReservationRequest entity = reservationRequestRepository.findByPublicId(res.getPublicId()).orElseThrow();
        entity.setExpiresAt(Instant.now().minusSeconds(10));
        reservationRequestRepository.saveAndFlush(entity);

        // Run scheduler
        expiryScheduler.processExpiredReservations();

        StockReservationRequest afterExpiry = reservationRequestRepository.findByPublicId(res.getPublicId()).orElseThrow();
        assertThat(afterExpiry.getStatus()).isEqualTo(ReservationStatus.EXPIRED);

        InventoryDetailResponse item = inventoryService.getInventoryByProductPublicId(prodId);
        assertThat(item.getReservedQuantity()).isEqualByComparingTo("0.000");
        assertThat(item.getAvailableQuantity()).isEqualByComparingTo("50.000");
    }

    // =========================================================================
    // PHASE 7 & 12: ALERTS & THRESHOLD MANAGEMENT
    // =========================================================================

    @Test
    @DisplayName("Alerts & Threshold: Updating threshold re-evaluates alerts and acknowledge updates state")
    void testAlertAndThresholdManagement() {
        String prodId = createAndInitializeProduct("KG", new BigDecimal("15.000"), new BigDecimal("10.000"));

        // Currently 15 > 10 (no alert)
        var alerts = stockAlertRepository.findByProductPublicIdAndAlertStatusIn(prodId, List.of(AlertStatus.ACTIVE));
        assertThat(alerts).isEmpty();

        // Update threshold to 20 -> should trigger LOW_STOCK alert (15 <= 20)
        inventoryService.updateThreshold(prodId, UpdateThresholdRequest.builder()
                .lowStockThreshold(new BigDecimal("20.000"))
                .build());

        var activeAlerts = stockAlertRepository.findByProductPublicIdAndAlertStatusIn(prodId, List.of(AlertStatus.ACTIVE));
        assertThat(activeAlerts).hasSize(1);
        StockAlert alert = activeAlerts.get(0);
        assertThat(alert.getAlertType()).isEqualTo(AlertType.LOW_STOCK);

        // Acknowledge alert
        StockAlertResponse ackRes = inventoryService.acknowledgeAlert(alert.getPublicId());
        assertThat(ackRes.getAlertStatus()).isEqualTo(AlertStatus.ACKNOWLEDGED);
    }

    // =========================================================================
    // PHASE 11: AVAILABILITY QUERY
    // =========================================================================

    @Test
    @DisplayName("Availability: Checks real-time quantity, inStock, and sufficiency")
    void testStockAvailabilityQuery() {
        String prodId = createAndInitializeProduct("KG", new BigDecimal("25.000"), new BigDecimal("5.000"));

        StockAvailabilityResponse avail1 = inventoryService.getAvailability(prodId, new BigDecimal("20.000"));
        assertThat(avail1.isInStock()).isTrue();
        assertThat(avail1.getSufficient()).isTrue();
        assertThat(avail1.getAvailableQuantity()).isEqualByComparingTo("25.000");

        StockAvailabilityResponse avail2 = inventoryService.getAvailability(prodId, new BigDecimal("30.000"));
        assertThat(avail2.isInStock()).isTrue();
        assertThat(avail2.getSufficient()).isFalse(); // 25 < 30
    }

    // =========================================================================
    // PHASE 13: OUTBOX DISPATCHER
    // =========================================================================

    @Test
    @DisplayName("Outbox Dispatcher: Dispatches pending events, marks PUBLISHED on success")
    void testOutboxDispatcherSuccess() throws Exception {
        String prodId = createAndInitializeProduct("KG", new BigDecimal("20.000"), new BigDecimal("5.000"));

        when(eventPublisher.publishStockEvent(anyString())).thenReturn(true);

        outboxDispatcher.dispatchOutboxEvents();

        var events = outboxRepository.findAll();
        assertThat(events.stream().anyMatch(e -> e.getProductPublicId().equals(prodId)
                && e.getEventStatus() == OutboxEventStatus.PUBLISHED)).isTrue();
    }
}
