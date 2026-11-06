package com.rrvgreenbasket.inventory.scheduler;

import com.rrvgreenbasket.inventory.entity.InventoryEventOutbox;
import com.rrvgreenbasket.inventory.enums.OutboxEventStatus;
import com.rrvgreenbasket.inventory.publisher.ProductStockEventPublisher;
import com.rrvgreenbasket.inventory.repository.InventoryEventOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxDispatcher {

    private final InventoryEventOutboxRepository outboxRepository;
    private final ProductStockEventPublisher eventPublisher;

    @Scheduled(fixedDelayString = "${app.inventory.outbox-poll-interval-ms:5000}")
    public void dispatchOutboxEvents() {
        recoverStaleInProgressEvents();

        List<InventoryEventOutbox> pendingEvents = outboxRepository
                .findTop50ByEventStatusAndRetryCountLessThanOrderByCreatedAtAsc(OutboxEventStatus.PENDING, 5);

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Found {} pending inventory outbox events to dispatch", pendingEvents.size());

        for (InventoryEventOutbox event : pendingEvents) {
            processSingleOutboxEvent(event.getId());
        }
    }

    @Transactional
    public void recoverStaleInProgressEvents() {
        try {
            Instant staleThreshold = Instant.now().minus(Duration.ofMinutes(5));
            List<InventoryEventOutbox> staleEvents = outboxRepository
                    .findByEventStatusAndCreatedAtBefore(OutboxEventStatus.IN_PROGRESS, staleThreshold);

            if (!staleEvents.isEmpty()) {
                log.warn("Recovering {} stale IN_PROGRESS outbox events back to PENDING", staleEvents.size());
                for (InventoryEventOutbox stale : staleEvents) {
                    stale.setEventStatus(OutboxEventStatus.PENDING);
                    outboxRepository.save(stale);
                }
            }
        } catch (Exception ex) {
            log.error("Failed to recover stale outbox events: {}", ex.getMessage());
        }
    }

    @Transactional
    public void processSingleOutboxEvent(Long eventId) {
        InventoryEventOutbox event = outboxRepository.findById(eventId).orElse(null);
        if (event == null || event.getEventStatus() != OutboxEventStatus.PENDING) {
            return;
        }

        event.setEventStatus(OutboxEventStatus.IN_PROGRESS);
        outboxRepository.saveAndFlush(event);

        try {
            boolean published = eventPublisher.publishStockEvent(event.getPayload());
            if (published) {
                event.setEventStatus(OutboxEventStatus.PUBLISHED);
                event.setPublishedAt(Instant.now());
                event.setLastError(null);
                outboxRepository.save(event);
                log.info("Published outbox stock event '{}' for product '{}'", event.getEventId(), event.getProductPublicId());
            } else {
                handleFailure(event, "Delivery rejected without exception");
            }
        } catch (Exception ex) {
            log.warn("Failed to publish outbox event '{}': {}", event.getEventId(), ex.getMessage());
            handleFailure(event, ex.getMessage());
        }
    }

    private void handleFailure(InventoryEventOutbox event, String errorMessage) {
        int retries = (event.getRetryCount() != null ? event.getRetryCount() : 0) + 1;
        event.setRetryCount(retries);
        event.setLastError(errorMessage != null && errorMessage.length() > 500 ? errorMessage.substring(0, 500) : errorMessage);

        int maxRetries = event.getMaxRetries() != null ? event.getMaxRetries() : 5;
        if (retries >= maxRetries) {
            event.setEventStatus(OutboxEventStatus.FAILED);
            log.error("Exhausted retries ({}/{}) for outbox event '{}'. Marked FAILED.", retries, maxRetries, event.getEventId());
        } else {
            event.setEventStatus(OutboxEventStatus.PENDING);
            log.debug("Scheduled outbox event '{}' for retry ({}/{})", event.getEventId(), retries, maxRetries);
        }
        outboxRepository.save(event);
    }
}
