package com.rrvgreenbasket.payment.entity;

import com.rrvgreenbasket.payment.enums.PaymentProvider;
import com.rrvgreenbasket.payment.enums.TransactionStatus;
import com.rrvgreenbasket.payment.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment_transactions", indexes = {
        @Index(name = "idx_tx_payment", columnList = "payment_id"),
        @Index(name = "idx_tx_provider_payment", columnList = "provider_payment_id"),
        @Index(name = "idx_tx_provider_order", columnList = "provider_order_id"),
        @Index(name = "idx_tx_status", columnList = "transaction_status"),
        @Index(name = "idx_tx_type", columnList = "transaction_type")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_public_id", nullable = false, unique = true, length = 36)
    private String transactionPublicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 40)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_status", nullable = false, length = 30)
    private TransactionStatus transactionStatus;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 10)
    @Builder.Default
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 30)
    @Builder.Default
    private PaymentProvider provider = PaymentProvider.RAZORPAY;

    @Column(name = "provider_order_id", length = 100)
    private String providerOrderId;

    @Column(name = "provider_payment_id", length = 100)
    private String providerPaymentId;

    @Column(name = "provider_reference", length = 100)
    private String providerReference;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_description", columnDefinition = "TEXT")
    private String failureDescription;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
