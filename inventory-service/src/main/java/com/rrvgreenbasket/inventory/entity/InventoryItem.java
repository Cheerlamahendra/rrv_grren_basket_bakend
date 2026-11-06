package com.rrvgreenbasket.inventory.entity;

import com.rrvgreenbasket.inventory.enums.StockStatus;
import com.rrvgreenbasket.inventory.enums.UnitOfMeasure;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_items", indexes = {
        @Index(name = "idx_inventory_items_product", columnList = "product_public_id", unique = true),
        @Index(name = "idx_inventory_items_unit", columnList = "unit_of_measure"),
        @Index(name = "idx_inventory_items_available", columnList = "available_quantity"),
        @Index(name = "idx_inventory_items_initialized", columnList = "initialized")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36, updatable = false)
    @Builder.Default
    private String publicId = UUID.randomUUID().toString();

    @Column(name = "product_public_id", nullable = false, unique = true, length = 36, updatable = false)
    private String productPublicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_of_measure", nullable = false, length = 20)
    private UnitOfMeasure unitOfMeasure;

    @Column(name = "physical_quantity", nullable = false, precision = 12, scale = 3)
    @Builder.Default
    private BigDecimal physicalQuantity = BigDecimal.ZERO.setScale(3);

    @Column(name = "reserved_quantity", nullable = false, precision = 12, scale = 3)
    @Builder.Default
    private BigDecimal reservedQuantity = BigDecimal.ZERO.setScale(3);

    @Column(name = "available_quantity", nullable = false, precision = 12, scale = 3)
    @Builder.Default
    private BigDecimal availableQuantity = BigDecimal.ZERO.setScale(3);

    @Column(name = "low_stock_threshold", nullable = false, precision = 12, scale = 3)
    @Builder.Default
    private BigDecimal lowStockThreshold = new BigDecimal("10.000");

    @Column(name = "initialized", nullable = false)
    @Builder.Default
    private Boolean initialized = false;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

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
        if (this.physicalQuantity == null) {
            this.physicalQuantity = BigDecimal.ZERO.setScale(3);
        }
        if (this.reservedQuantity == null) {
            this.reservedQuantity = BigDecimal.ZERO.setScale(3);
        }
        if (this.lowStockThreshold == null) {
            this.lowStockThreshold = new BigDecimal("10.000");
        }
        if (this.initialized == null) {
            this.initialized = false;
        }
        recalculateAvailable();
    }

    @PreUpdate
    public void ensureInvariantsOnUpdate() {
        recalculateAvailable();
    }

    public void recalculateAvailable() {
        if (this.physicalQuantity == null) {
            this.physicalQuantity = BigDecimal.ZERO.setScale(3);
        }
        if (this.reservedQuantity == null) {
            this.reservedQuantity = BigDecimal.ZERO.setScale(3);
        }
        this.availableQuantity = this.physicalQuantity.subtract(this.reservedQuantity);
        if (this.availableQuantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Available quantity cannot be negative for product: " + this.productPublicId);
        }
    }

    public StockStatus calculateStockStatus() {
        if (this.availableQuantity == null || this.availableQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            return StockStatus.OUT_OF_STOCK;
        }
        if (this.lowStockThreshold != null && this.availableQuantity.compareTo(this.lowStockThreshold) <= 0) {
            return StockStatus.LOW_STOCK;
        }
        return StockStatus.IN_STOCK;
    }
}
