package com.rrvgreenbasket.inventory.entity;

import com.rrvgreenbasket.inventory.enums.ReservationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "stock_reservation_requests", indexes = {
        @Index(name = "idx_res_requests_order", columnList = "order_public_id"),
        @Index(name = "idx_res_requests_status", columnList = "status"),
        @Index(name = "idx_res_requests_expires", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockReservationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36, updatable = false)
    @Builder.Default
    private String publicId = UUID.randomUUID().toString();

    @Column(name = "order_public_id", nullable = false, length = 36)
    private String orderPublicId;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100, updatable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private ReservationStatus status = ReservationStatus.ACTIVE;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @OneToMany(mappedBy = "reservationRequest", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<StockReservationItem> items = new ArrayList<>();

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
        if (this.status == null) {
            this.status = ReservationStatus.ACTIVE;
        }
    }

    public void addItem(StockReservationItem item) {
        if (this.items == null) {
            this.items = new ArrayList<>();
        }
        this.items.add(item);
        item.setReservationRequest(this);
    }

    public void removeItem(StockReservationItem item) {
        if (this.items != null) {
            this.items.remove(item);
            item.setReservationRequest(null);
        }
    }
}
