package com.rrvgreenbasket.inventory.entity;

import com.rrvgreenbasket.inventory.enums.MovementType;
import com.rrvgreenbasket.inventory.enums.UnitOfMeasure;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_movements", indexes = {
        @Index(name = "idx_movements_product", columnList = "product_public_id"),
        @Index(name = "idx_movements_type", columnList = "movement_type"),
        @Index(name = "idx_movements_created_at", columnList = "created_at"),
        @Index(name = "idx_movements_reference", columnList = "reference_type, reference_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36, updatable = false)
    @Builder.Default
    private String publicId = UUID.randomUUID().toString();

    @Column(name = "product_public_id", nullable = false, length = 36, updatable = false)
    private String productPublicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 40, updatable = false)
    private MovementType movementType;

    @Column(name = "quantity", nullable = false, precision = 12, scale = 3, updatable = false)
    private BigDecimal quantity;

    @Column(name = "physical_quantity_before", nullable = false, precision = 12, scale = 3, updatable = false)
    private BigDecimal physicalQuantityBefore;

    @Column(name = "physical_quantity_after", nullable = false, precision = 12, scale = 3, updatable = false)
    private BigDecimal physicalQuantityAfter;

    @Column(name = "available_quantity_before", nullable = false, precision = 12, scale = 3, updatable = false)
    private BigDecimal availableQuantityBefore;

    @Column(name = "available_quantity_after", nullable = false, precision = 12, scale = 3, updatable = false)
    private BigDecimal availableQuantityAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_of_measure", nullable = false, length = 20, updatable = false)
    private UnitOfMeasure unitOfMeasure;

    @Column(name = "reason", nullable = false, length = 100, updatable = false)
    private String reason;

    @Column(name = "reference_type", length = 50, updatable = false)
    private String referenceType;

    @Column(name = "reference_id", length = 100, updatable = false)
    private String referenceId;

    @Column(name = "notes", length = 500, updatable = false)
    private String notes;

    @Column(name = "performed_by", nullable = false, length = 100, updatable = false)
    private String performedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void ensureInvariantsOnPersist() {
        if (this.publicId == null || this.publicId.isBlank()) {
            this.publicId = UUID.randomUUID().toString();
        }
        if (this.quantity != null) {
            this.quantity = this.quantity.setScale(3, RoundingMode.HALF_UP);
        }
        if (this.physicalQuantityBefore != null) {
            this.physicalQuantityBefore = this.physicalQuantityBefore.setScale(3, RoundingMode.HALF_UP);
        }
        if (this.physicalQuantityAfter != null) {
            this.physicalQuantityAfter = this.physicalQuantityAfter.setScale(3, RoundingMode.HALF_UP);
        }
        if (this.availableQuantityBefore != null) {
            this.availableQuantityBefore = this.availableQuantityBefore.setScale(3, RoundingMode.HALF_UP);
        }
        if (this.availableQuantityAfter != null) {
            this.availableQuantityAfter = this.availableQuantityAfter.setScale(3, RoundingMode.HALF_UP);
        }
    }
}
