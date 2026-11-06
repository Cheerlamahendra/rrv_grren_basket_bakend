package com.rrvgreenbasket.inventory;

import com.rrvgreenbasket.inventory.entity.*;
import com.rrvgreenbasket.inventory.enums.*;
import com.rrvgreenbasket.inventory.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration;
import org.springframework.cloud.netflix.eureka.EurekaDiscoveryClientConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
@Transactional
class RepositoryTests {

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

    @Test
    @DisplayName("Verify InventoryItemRepository CRUD and pessimistic locking")
    void testInventoryItemRepositoryCrudAndLock() {
        String prodId = UUID.randomUUID().toString();
        InventoryItem item = InventoryItem.builder()
                .productPublicId(prodId)
                .unitOfMeasure(UnitOfMeasure.KG)
                .physicalQuantity(new BigDecimal("100.000"))
                .reservedQuantity(new BigDecimal("20.000"))
                .availableQuantity(new BigDecimal("80.000"))
                .lowStockThreshold(new BigDecimal("15.000"))
                .initialized(true)
                .build();

        InventoryItem saved = inventoryItemRepository.saveAndFlush(item);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPublicId()).isNotBlank();
        assertThat(saved.getVersion()).isEqualTo(0L);

        // Find by productPublicId
        Optional<InventoryItem> found = inventoryItemRepository.findByProductPublicId(prodId);
        assertThat(found).isPresent();
        assertThat(found.get().getAvailableQuantity()).isEqualByComparingTo(new BigDecimal("80.000"));

        // Pessimistic write lock query
        Optional<InventoryItem> locked = inventoryItemRepository.findByProductPublicIdWithLock(prodId);
        assertThat(locked).isPresent();

        // Multi-product pessimistic write lock query
        List<InventoryItem> lockedList = inventoryItemRepository.findByProductPublicIdInWithLock(List.of(prodId));
        assertThat(lockedList).hasSize(1);
    }

    @Test
    @DisplayName("Verify reservation parent-child cascading and idempotency key lookup")
    void testReservationRequestAndItemsCascading() {
        String orderId = UUID.randomUUID().toString();
        String idemKey = "IDEM-" + UUID.randomUUID();
        String prod1 = UUID.randomUUID().toString();
        String prod2 = UUID.randomUUID().toString();

        StockReservationRequest request = StockReservationRequest.builder()
                .orderPublicId(orderId)
                .idempotencyKey(idemKey)
                .status(ReservationStatus.ACTIVE)
                .expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
                .build();

        StockReservationItem item1 = StockReservationItem.builder()
                .orderPublicId(orderId)
                .productPublicId(prod1)
                .reservedQuantity(new BigDecimal("5.000"))
                .unitOfMeasure(UnitOfMeasure.KG)
                .itemStatus(ReservationStatus.ACTIVE)
                .build();

        StockReservationItem item2 = StockReservationItem.builder()
                .orderPublicId(orderId)
                .productPublicId(prod2)
                .reservedQuantity(new BigDecimal("2.000"))
                .unitOfMeasure(UnitOfMeasure.PACK)
                .itemStatus(ReservationStatus.ACTIVE)
                .build();

        request.addItem(item1);
        request.addItem(item2);

        StockReservationRequest saved = reservationRequestRepository.saveAndFlush(request);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getItems()).hasSize(2);
        assertThat(saved.getItems().get(0).getId()).isNotNull();
        assertThat(saved.getItems().get(0).getReservationRequest().getId()).isEqualTo(saved.getId());

        // Idempotency lookup
        Optional<StockReservationRequest> byKey = reservationRequestRepository.findByIdempotencyKey(idemKey);
        assertThat(byKey).isPresent();
        assertThat(byKey.get().getOrderPublicId()).isEqualTo(orderId);

        // Idempotency lock lookup
        Optional<StockReservationRequest> lockedKey = reservationRequestRepository.findByIdempotencyKeyWithLock(idemKey);
        assertThat(lockedKey).isPresent();

        // Child items query
        List<StockReservationItem> items = reservationItemRepository.findByReservationRequestId(saved.getId());
        assertThat(items).hasSize(2);
    }

    @Test
    @DisplayName("Verify StockMovement persistence and query by product and type")
    void testStockMovementPersistence() {
        String prodId = UUID.randomUUID().toString();
        StockMovement movement = StockMovement.builder()
                .productPublicId(prodId)
                .movementType(MovementType.STOCK_IN)
                .quantity(new BigDecimal("50.000"))
                .physicalQuantityBefore(BigDecimal.ZERO)
                .physicalQuantityAfter(new BigDecimal("50.000"))
                .availableQuantityBefore(BigDecimal.ZERO)
                .availableQuantityAfter(new BigDecimal("50.000"))
                .unitOfMeasure(UnitOfMeasure.DOZEN)
                .reason("Supplier batch arrival")
                .referenceType("GRN")
                .referenceId("GRN-998877")
                .notes("Verified undamaged")
                .performedBy("admin@rrv.com")
                .build();

        StockMovement saved = stockMovementRepository.saveAndFlush(movement);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPublicId()).isNotBlank();

        List<StockMovement> refMovements = stockMovementRepository.findByReferenceTypeAndReferenceId("GRN", "GRN-998877");
        assertThat(refMovements).hasSize(1);
        assertThat(refMovements.get(0).getProductPublicId()).isEqualTo(prodId);
    }

    @Test
    @DisplayName("Verify StockAlert persistence and query by status and type")
    void testStockAlertPersistence() {
        String prodId = UUID.randomUUID().toString();
        StockAlert alert = StockAlert.builder()
                .productPublicId(prodId)
                .alertType(AlertType.LOW_STOCK)
                .currentQuantity(new BigDecimal("3.000"))
                .thresholdQuantity(new BigDecimal("10.000"))
                .unitOfMeasure(UnitOfMeasure.KG)
                .alertStatus(AlertStatus.ACTIVE)
                .build();

        StockAlert saved = stockAlertRepository.saveAndFlush(alert);
        assertThat(saved.getId()).isNotNull();

        Optional<StockAlert> found = stockAlertRepository.findByProductPublicIdAndAlertTypeAndAlertStatusIn(
                prodId, AlertType.LOW_STOCK, List.of(AlertStatus.ACTIVE, AlertStatus.ACKNOWLEDGED)
        );
        assertThat(found).isPresent();
        assertThat(found.get().getCurrentQuantity()).isEqualByComparingTo(new BigDecimal("3.000"));
    }

    @Test
    @DisplayName("Verify InventoryEventOutbox persistence and status ordering query")
    void testInventoryEventOutboxPersistence() {
        String prodId = UUID.randomUUID().toString();
        InventoryEventOutbox outbox = InventoryEventOutbox.builder()
                .productPublicId(prodId)
                .eventType("STOCK_INITIALIZED")
                .payload("{\"productId\":\"" + prodId + "\",\"unit\":\"KG\",\"available\":100}")
                .eventStatus(OutboxEventStatus.PENDING)
                .retryCount(0)
                .maxRetries(5)
                .build();

        InventoryEventOutbox saved = outboxRepository.saveAndFlush(outbox);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEventId()).isNotBlank();

        List<InventoryEventOutbox> pending = outboxRepository.findTop50ByEventStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING);
        assertThat(pending).isNotEmpty();
        assertThat(pending.stream().anyMatch(e -> e.getProductPublicId().equals(prodId))).isTrue();
    }
}
