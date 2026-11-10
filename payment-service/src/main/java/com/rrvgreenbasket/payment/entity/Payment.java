package com.rrvgreenbasket.payment.entity;

import com.rrvgreenbasket.payment.enums.PaymentMethod;
import com.rrvgreenbasket.payment.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payments_order", columnList = "order_public_id"),
        @Index(name = "idx_payments_customer", columnList = "customer_public_id"),
        @Index(name = "idx_payments_status", columnList = "payment_status"),
        @Index(name = "idx_payments_method", columnList = "payment_method"),
        @Index(name = "idx_payments_created_at", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_public_id", nullable = false, unique = true, length = 36)
    private String paymentPublicId;

    @Column(name = "order_public_id", nullable = false, length = 36)
    private String orderPublicId;

    @Column(name = "customer_public_id", nullable = false, length = 36)
    private String customerPublicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 40)
    private PaymentStatus paymentStatus;

    @Column(name = "currency", nullable = false, length = 10)
    @Builder.Default
    private String currency = "INR";

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "advance_percentage", nullable = false)
    @Builder.Default
    private Integer advancePercentage = 100;

    @Column(name = "advance_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal advanceAmount;

    @Column(name = "advance_paid_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal advancePaidAmount = BigDecimal.ZERO;

    @Column(name = "delivery_collection_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal deliveryCollectionAmount = BigDecimal.ZERO;

    @Column(name = "delivery_collected_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal deliveryCollectedAmount = BigDecimal.ZERO;

    @Column(name = "outstanding_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal outstandingAmount;

    @Column(name = "refunded_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PaymentTransaction> transactions = new ArrayList<>();

    public boolean isPartialCod() {
        return this.paymentMethod == PaymentMethod.PARTIAL_ONLINE_COD;
    }

    public boolean isFullyPaid() {
        return this.paymentStatus == PaymentStatus.PAID;
    }

    public void addTransaction(PaymentTransaction transaction) {
        transactions.add(transaction);
        transaction.setPayment(this);
    }
}
