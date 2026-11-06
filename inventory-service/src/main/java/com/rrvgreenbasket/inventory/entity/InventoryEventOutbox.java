package com.rrvgreenbasket.inventory.entity;

import com.rrvgreenbasket.inventory.enums.OutboxEventStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_event_outbox", indexes = {
        @Index(name = "idx_outbox_status_retry", columnList = "event_status, retry_count"),
        @Index(name = "idx_outbox_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryEventOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 36, updatable = false)
    @Builder.Default
    private String eventId = UUID.randomUUID().toString();

    @Column(name = "product_public_id", nullable = false, length = 36)
    private String productPublicId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "json")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_status", nullable = false, length = 20)
    @Builder.Default
    private OutboxEventStatus eventStatus = OutboxEventStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    @Builder.Default
    private Integer maxRetries = 5;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @PrePersist
    public void ensureInvariantsOnPersist() {
        if (this.eventId == null || this.eventId.isBlank()) {
            this.eventId = UUID.randomUUID().toString();
        }
        if (this.eventStatus == null) {
            this.eventStatus = OutboxEventStatus.PENDING;
        }
        if (this.retryCount == null) {
            this.retryCount = 0;
        }
        if (this.maxRetries == null) {
            this.maxRetries = 5;
        }
    }
}
