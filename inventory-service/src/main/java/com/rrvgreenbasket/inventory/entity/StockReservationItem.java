package com.rrvgreenbasket.inventory.entity;

import com.rrvgreenbasket.inventory.enums.ReservationStatus;
import com.rrvgreenbasket.inventory.enums.UnitOfMeasure;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_reservation_items", indexes = {
        @Index(name = "idx_res_items_request", columnList = "reservation_request_id"),
        @Index(name = "idx_res_items_product", columnList = "product_public_id"),
        @Index(name = "idx_res_items_order", columnList = "order_public_id"),
        @Index(name = "idx_res_items_status", columnList = "item_status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockReservationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36, updatable = false)
    @Builder.Default
    private String publicId = UUID.randomUUID().toString();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_request_id", nullable = false)
    private StockReservationRequest reservationRequest;

    @Column(name = "order_public_id", nullable = false, length = 36)
    private String orderPublicId;

    @Column(name = "product_public_id", nullable = false, length = 36)
    private String productPublicId;

    @Column(name = "reserved_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal reservedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_of_measure", nullable = false, length = 20)
    private UnitOfMeasure unitOfMeasure;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_status", nullable = false, length = 30)
    @Builder.Default
    private ReservationStatus itemStatus = ReservationStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void ensureInvariantsOnPersist() {
        if (this.publicId == null || this.publicId.isBlank()) {
            this.publicId = UUID.randomUUID().toString();
        }
        if (this.itemStatus == null) {
            this.itemStatus = ReservationStatus.ACTIVE;
        }
        if (this.reservedQuantity != null) {
            this.reservedQuantity = this.reservedQuantity.setScale(3, RoundingMode.HALF_UP);
        }
    }
}
