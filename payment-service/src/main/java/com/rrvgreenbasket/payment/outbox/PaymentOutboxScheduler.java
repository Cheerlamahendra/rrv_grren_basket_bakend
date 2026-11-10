package com.rrvgreenbasket.payment.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.payment.client.OrderServiceClient;
import com.rrvgreenbasket.payment.dto.event.PaymentEventPayload;
import com.rrvgreenbasket.payment.entity.PaymentEventOutbox;
import com.rrvgreenbasket.payment.enums.OutboxStatus;
import com.rrvgreenbasket.payment.repository.PaymentEventOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentOutboxScheduler {

    private final PaymentEventOutboxRepository outboxRepository;
    private final OrderServiceClient orderServiceClient;
    private final ObjectMapper objectMapper;

    @Value("${payment.outbox.batch-size:50}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${payment.outbox.poll-interval-ms:5000}")
    @Transactional
    public void processOutboxEvents() {
        List<PaymentEventOutbox> pendingEvents = outboxRepository
                .findByPublishingStatusAndRetryCountLessThanOrderByCreatedAtAsc(
                        OutboxStatus.PENDING, 5, PageRequest.of(0, batchSize));

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Found {} pending outbox event(s) to process", pendingEvents.size());

        for (PaymentEventOutbox event : pendingEvents) {
            try {
                PaymentEventPayload payload = objectMapper.readValue(event.getEventPayload(), PaymentEventPayload.class);
                orderServiceClient.notifyPaymentStatus(payload.getOrderPublicId(), event.getEventType(), payload);

                event.setPublishingStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(Instant.now());
                event.setLastError(null);
                log.info("Successfully published outbox event '{}' ({})", event.getEventPublicId(), event.getEventType());

            } catch (Exception ex) {
                int newRetry = event.getRetryCount() + 1;
                event.setRetryCount(newRetry);
                event.setLastError(ex.getMessage());
                if (newRetry >= event.getMaxRetries()) {
                    event.setPublishingStatus(OutboxStatus.FAILED);
                    log.error("Outbox event '{}' reached max retries ({}). Marked as FAILED.",
                            event.getEventPublicId(), event.getMaxRetries());
                } else {
                    log.warn("Failed to publish outbox event '{}' (attempt {}/{}): {}",
                            event.getEventPublicId(), newRetry, event.getMaxRetries(), ex.getMessage());
                }
            }
            outboxRepository.save(event);
        }
    }
}
