package com.rrvgreenbasket.payment.entity;

import com.rrvgreenbasket.payment.enums.OutboxStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "payment_event_outbox", indexes = {
        @Index(name = "idx_outbox_status_retry", columnList = "publishing_status, retry_count"),
        @Index(name = "idx_outbox_created_at", columnList = "created_at"),
        @Index(name = "idx_outbox_aggregate", columnList = "aggregate_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEventOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_public_id", nullable = false, unique = true, length = 36)
    private String eventPublicId;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    @Builder.Default
    private String aggregateType = "PAYMENT";

    @Column(name = "aggregate_id", nullable = false, length = 36)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(name = "event_payload", nullable = false, columnDefinition = "LONGTEXT")
    private String eventPayload;

    @Enumerated(EnumType.STRING)
    @Column(name = "publishing_status", nullable = false, length = 20)
    @Builder.Default
    private OutboxStatus publishingStatus = OutboxStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    @Builder.Default
    private Integer maxRetries = 5;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;
}
