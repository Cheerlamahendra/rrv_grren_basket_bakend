package com.rrvgreenbasket.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "cod_collections", indexes = {
        @Index(name = "idx_cod_payment", columnList = "payment_id"),
        @Index(name = "idx_cod_order", columnList = "order_public_id"),
        @Index(name = "idx_cod_delivery_ref", columnList = "delivery_reference"),
        @Index(name = "idx_cod_collector_ref", columnList = "collector_reference")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodCollection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "collection_public_id", nullable = false, unique = true, length = 36)
    private String collectionPublicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "order_public_id", nullable = false, length = 36)
    private String orderPublicId;

    @Column(name = "collected_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal collectedAmount;

    @Column(name = "collector_reference", nullable = false, length = 100)
    private String collectorReference;

    @Column(name = "delivery_reference", nullable = false, length = 100)
    private String deliveryReference;

    @Column(name = "collection_status", nullable = false, length = 30)
    @Builder.Default
    private String collectionStatus = "COLLECTED";

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
