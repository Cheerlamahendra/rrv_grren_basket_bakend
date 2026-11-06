package com.rrvgreenbasket.inventory.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.inventory.dto.response.OutboxEventPayload;
import com.rrvgreenbasket.inventory.entity.InventoryEventOutbox;
import com.rrvgreenbasket.inventory.entity.InventoryItem;
import com.rrvgreenbasket.inventory.entity.StockAlert;
import com.rrvgreenbasket.inventory.entity.StockReservationItem;
import com.rrvgreenbasket.inventory.entity.StockReservationRequest;
import com.rrvgreenbasket.inventory.enums.AlertStatus;
import com.rrvgreenbasket.inventory.enums.AlertType;
import com.rrvgreenbasket.inventory.enums.OutboxEventStatus;
import com.rrvgreenbasket.inventory.enums.ReservationStatus;
import com.rrvgreenbasket.inventory.enums.UnitOfMeasure;
import com.rrvgreenbasket.inventory.repository.InventoryEventOutboxRepository;
import com.rrvgreenbasket.inventory.repository.InventoryItemRepository;
import com.rrvgreenbasket.inventory.repository.StockAlertRepository;
import com.rrvgreenbasket.inventory.repository.StockReservationRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpiryScheduler {

    private final StockReservationRequestRepository reservationRequestRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final StockAlertRepository stockAlertRepository;
    private final InventoryEventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${app.inventory.reservation-check-interval-ms:60000}")
    @Transactional
    public void processExpiredReservations() {
        Instant now = Instant.now();
        List<StockReservationRequest> expiredRequests = reservationRequestRepository
                .findByStatusAndExpiresAtBefore(ReservationStatus.ACTIVE, now);

        if (expiredRequests.isEmpty()) {
            return;
        }

        log.info("Found {} expired reservations to release", expiredRequests.size());
        for (StockReservationRequest reservation : expiredRequests) {
            try {
                expireReservation(reservation.getId());
            } catch (Exception ex) {
                log.error("Failed to expire reservation id={}: {}", reservation.getId(), ex.getMessage(), ex);
            }
        }
    }

    @Transactional
    public void expireReservation(Long reservationId) {
        StockReservationRequest reservation = reservationRequestRepository.findById(reservationId).orElse(null);
        if (reservation == null || reservation.getStatus() != ReservationStatus.ACTIVE) {
            return;
        }

        if (reservation.getExpiresAt().isAfter(Instant.now())) {
            return;
        }

        log.info("Expiring reservation '{}' for order '{}'", reservation.getPublicId(), reservation.getOrderPublicId());

        List<String> productIds = reservation.getItems().stream()
                .map(StockReservationItem::getProductPublicId)
                .distinct()
                .sorted()
                .toList();

        List<InventoryItem> lockedItems = inventoryItemRepository.findByProductPublicIdInWithLock(productIds);
        Map<String, InventoryItem> itemMap = lockedItems.stream()
                .collect(Collectors.toMap(InventoryItem::getProductPublicId, i -> i));

        for (StockReservationItem item : reservation.getItems()) {
            InventoryItem invItem = itemMap.get(item.getProductPublicId());
            if (invItem != null) {
                BigDecimal qty = item.getReservedQuantity();
                invItem.setReservedQuantity(invItem.getReservedQuantity().subtract(qty));
                invItem.recalculateAvailable();

                item.setItemStatus(ReservationStatus.EXPIRED);
            }
        }

        for (InventoryItem invItem : lockedItems) {
            inventoryItemRepository.save(invItem);
            evaluateAlertLifecycle(invItem);
            createOutboxEvent(invItem, "RESERVATION_EXPIRED");
        }

        reservation.setStatus(ReservationStatus.EXPIRED);
        reservationRequestRepository.save(reservation);

        log.info("Expired reservation '{}' and released reserved quantities", reservation.getPublicId());
    }

    private void evaluateAlertLifecycle(InventoryItem item) {
        BigDecimal available = item.getAvailableQuantity();
        BigDecimal threshold = item.getLowStockThreshold() != null ? item.getLowStockThreshold() : new BigDecimal("10.000");
        String pId = item.getProductPublicId();
        UnitOfMeasure uom = item.getUnitOfMeasure();

        if (available.compareTo(BigDecimal.ZERO) <= 0) {
            resolveAlertIfExists(pId, AlertType.LOW_STOCK);
            createAlertIfNotExists(pId, AlertType.OUT_OF_STOCK, BigDecimal.ZERO.setScale(3), threshold, uom);
        } else if (available.compareTo(threshold) <= 0) {
            resolveAlertIfExists(pId, AlertType.OUT_OF_STOCK);
            createAlertIfNotExists(pId, AlertType.LOW_STOCK, available, threshold, uom);
        } else {
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
            } catch (Exception ignored) {
            }
        }
    }

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
            log.error("Failed to create outbox event for expired reservation item: {}", item.getProductPublicId(), ex);
        }
    }
}
