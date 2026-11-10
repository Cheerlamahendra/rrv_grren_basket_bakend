package com.rrvgreenbasket.payment.entity;

import com.rrvgreenbasket.payment.enums.IdempotencyStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "payment_idempotency_keys", indexes = {
        @Index(name = "idx_idempotency_type", columnList = "operation_type"),
        @Index(name = "idx_idempotency_expires", columnList = "expires_at"),
        @Index(name = "idx_idempotency_resource", columnList = "resource_reference")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentIdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "operation_type", nullable = false, length = 50)
    private String operationType;

    @Column(name = "resource_reference", nullable = false, length = 100)
    private String resourceReference;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "response_reference", length = 100)
    private String responseReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private IdempotencyStatus status = IdempotencyStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
