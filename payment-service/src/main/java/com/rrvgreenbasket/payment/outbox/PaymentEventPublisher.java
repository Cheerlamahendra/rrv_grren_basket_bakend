package com.rrvgreenbasket.payment.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.payment.dto.event.PaymentEventPayload;
import com.rrvgreenbasket.payment.entity.Payment;
import com.rrvgreenbasket.payment.entity.PaymentEventOutbox;
import com.rrvgreenbasket.payment.enums.OutboxStatus;
import com.rrvgreenbasket.payment.repository.PaymentEventOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventPublisher {

    private final PaymentEventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void publishEvent(Payment payment, String eventType, String providerPaymentId, String providerOrderId, String failureReason) {
        try {
            String eventId = UUID.randomUUID().toString();

            PaymentEventPayload payload = PaymentEventPayload.builder()
                    .eventId(eventId)
                    .eventType(eventType)
                    .paymentPublicId(payment.getPaymentPublicId())
                    .orderPublicId(payment.getOrderPublicId())
                    .totalAmount(payment.getTotalAmount())
                    .advanceAmount(payment.getAdvanceAmount())
                    .advancePaidAmount(payment.getAdvancePaidAmount())
                    .deliveryCollectionAmount(payment.getDeliveryCollectionAmount())
                    .deliveryCollectedAmount(payment.getDeliveryCollectedAmount())
                    .outstandingAmount(payment.getOutstandingAmount())
                    .currency(payment.getCurrency())
                    .paymentMethod(payment.getPaymentMethod())
                    .paymentStatus(payment.getPaymentStatus())
                    .providerPaymentId(providerPaymentId)
                    .providerOrderId(providerOrderId)
                    .failureReason(failureReason)
                    .occurredAt(Instant.now())
                    .build();

            String payloadJson = objectMapper.writeValueAsString(payload);

            PaymentEventOutbox outboxEntry = PaymentEventOutbox.builder()
                    .eventPublicId(eventId)
                    .aggregateType("PAYMENT")
                    .aggregateId(payment.getPaymentPublicId())
                    .eventType(eventType)
                    .eventPayload(payloadJson)
                    .publishingStatus(OutboxStatus.PENDING)
                    .retryCount(0)
                    .maxRetries(5)
                    .build();

            outboxRepository.save(outboxEntry);
            log.info("Persisted transactional outbox event '{}' for payment '{}', order '{}'",
                    eventType, payment.getPaymentPublicId(), payment.getOrderPublicId());

        } catch (Exception ex) {
            log.error("Failed to persist outbox event '{}' for payment '{}': {}",
                    eventType, payment.getPaymentPublicId(), ex.getMessage(), ex);
            throw new RuntimeException("Could not persist payment outbox event: " + ex.getMessage(), ex);
        }
    }
}
