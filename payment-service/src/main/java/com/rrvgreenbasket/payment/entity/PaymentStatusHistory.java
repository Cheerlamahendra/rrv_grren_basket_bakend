package com.rrvgreenbasket.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "payment_status_history", indexes = {
        @Index(name = "idx_history_payment", columnList = "payment_id"),
        @Index(name = "idx_history_changed_at", columnList = "changed_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "previous_status", length = 40)
    private String previousStatus;

    @Column(name = "new_status", nullable = false, length = 40)
    private String newStatus;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "changed_by", nullable = false, length = 100)
    private String changedBy;

    @CreationTimestamp
    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;
}
