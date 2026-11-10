package com.rrvgreenbasket.payment.entity;

import com.rrvgreenbasket.payment.enums.PaymentProvider;
import com.rrvgreenbasket.payment.enums.WebhookProcessingStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "payment_webhook_events", uniqueConstraints = {
        @UniqueConstraint(name = "uq_webhook_provider_event", columnNames = {"provider", "provider_event_id"})
}, indexes = {
        @Index(name = "idx_webhook_status", columnList = "processing_status"),
        @Index(name = "idx_webhook_received", columnList = "received_at"),
        @Index(name = "idx_webhook_type", columnList = "event_type")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 30)
    @Builder.Default
    private PaymentProvider provider = PaymentProvider.RAZORPAY;

    @Column(name = "provider_event_id", nullable = false, length = 100)
    private String providerEventId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(name = "event_payload", nullable = false, columnDefinition = "LONGTEXT")
    private String eventPayload;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 30)
    @Builder.Default
    private WebhookProcessingStatus processingStatus = WebhookProcessingStatus.RECEIVED;

    @CreationTimestamp
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}
