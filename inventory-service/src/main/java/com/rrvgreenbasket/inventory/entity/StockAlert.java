package com.rrvgreenbasket.inventory.entity;

import com.rrvgreenbasket.inventory.enums.AlertStatus;
import com.rrvgreenbasket.inventory.enums.AlertType;
import com.rrvgreenbasket.inventory.enums.UnitOfMeasure;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_alerts", indexes = {
        @Index(name = "idx_alerts_product_status", columnList = "product_public_id, alert_type, alert_status"),
        @Index(name = "idx_alerts_status", columnList = "alert_status"),
        @Index(name = "idx_alerts_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36, updatable = false)
    @Builder.Default
    private String publicId = UUID.randomUUID().toString();

    @Column(name = "product_public_id", nullable = false, length = 36)
    private String productPublicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 30)
    private AlertType alertType;

    @Column(name = "current_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal currentQuantity;

    @Column(name = "threshold_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal thresholdQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_of_measure", nullable = false, length = 20)
    private UnitOfMeasure unitOfMeasure;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_status", nullable = false, length = 30)
    @Builder.Default
    private AlertStatus alertStatus = AlertStatus.ACTIVE;

    @Column(name = "unresolved_marker", insertable = false, updatable = false, length = 70)
    private String unresolvedMarker;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @PrePersist
    public void ensureInvariantsOnPersist() {
        if (this.publicId == null || this.publicId.isBlank()) {
            this.publicId = UUID.randomUUID().toString();
        }
        if (this.alertStatus == null) {
            this.alertStatus = AlertStatus.ACTIVE;
        }
        if (this.currentQuantity != null) {
            this.currentQuantity = this.currentQuantity.setScale(3, RoundingMode.HALF_UP);
        }
        if (this.thresholdQuantity != null) {
            this.thresholdQuantity = this.thresholdQuantity.setScale(3, RoundingMode.HALF_UP);
        }
    }
}
