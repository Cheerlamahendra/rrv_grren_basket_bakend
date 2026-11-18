package com.rrvgreenbasket.order.entity;

import com.rrvgreenbasket.order.enums.OrderStatus;
import com.rrvgreenbasket.order.enums.PaymentMethod;
import com.rrvgreenbasket.order.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "orders",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_orders_public_id", columnNames = "public_id"),
                @UniqueConstraint(name = "uk_orders_number", columnNames = "order_number")
        },
        indexes = {
                @Index(name = "idx_orders_customer", columnList = "customer_public_id"),
                @Index(name = "idx_orders_status", columnList = "order_status"),
                @Index(name = "idx_orders_number", columnList = "order_number"),
                @Index(name = "idx_orders_placed_at", columnList = "order_placed_at"),
                @Index(name = "idx_orders_delivery_date", columnList = "promised_delivery_date"),
                @Index(name = "idx_orders_idempotency", columnList = "customer_public_id, idempotency_key")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", length = 36, nullable = false, unique = true, columnDefinition = "CHAR(36)")
    private String publicId;

    @Column(name = "order_number", length = 30, nullable = false, unique = true)
    private String orderNumber;

    @Column(name = "customer_public_id", length = 36, nullable = false, columnDefinition = "CHAR(36)")
    private String customerPublicId;

    @Column(name = "delivery_zone_public_id", length = 36, nullable = false, columnDefinition = "CHAR(36)")
    private String deliveryZonePublicId;

    @Column(name = "delivery_zone_name_snapshot", length = 100, nullable = false)
    private String deliveryZoneNameSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", length = 30, nullable = false)
    private OrderStatus orderStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 30, nullable = false)
    private PaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 30, nullable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "subtotal", precision = 12, scale = 2, nullable = false)
    private BigDecimal subtotal;

    @Column(name = "discount_amount", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "delivery_charge", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal deliveryCharge = BigDecimal.ZERO;

    @Column(name = "total_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "minimum_order_amount_snapshot", precision = 12, scale = 2, nullable = false)
    private BigDecimal minimumOrderAmountSnapshot;

    @Column(name = "delivery_address_snapshot", columnDefinition = "TEXT", nullable = false)
    private String deliveryAddressSnapshot;

    @Column(name = "delivery_latitude", precision = 10, scale = 7, nullable = false)
    private BigDecimal deliveryLatitude;

    @Column(name = "delivery_longitude", precision = 10, scale = 7, nullable = false)
    private BigDecimal deliveryLongitude;

    @Column(name = "promised_delivery_date", nullable = false)
    private LocalDate promisedDeliveryDate;

    @Column(name = "order_placed_at", nullable = false)
    private LocalDateTime orderPlacedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }
}
