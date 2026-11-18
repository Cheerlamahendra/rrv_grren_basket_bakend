package com.rrvgreenbasket.delivery.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "delivery_pricing_slabs",
        indexes = {
                @Index(name = "idx_delivery_pricing_slabs_active", columnList = "active"),
                @Index(name = "idx_delivery_pricing_slabs_distance", columnList = "minimum_distance_meters, maximum_distance_meters")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryPricingSlab {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "minimum_distance_meters", nullable = false)
    private Integer minimumDistanceMeters;

    @Column(name = "maximum_distance_meters", nullable = false)
    private Integer maximumDistanceMeters;

    @Column(name = "delivery_charge", precision = 10, scale = 2, nullable = false)
    private BigDecimal deliveryCharge;

    @Column(name = "currency", length = 10, nullable = false)
    @Builder.Default
    private String currency = "INR";

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "effective_start_date")
    private LocalDateTime effectiveStartDate;

    @Column(name = "effective_end_date")
    private LocalDateTime effectiveEndDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    /**
     * Checks if this slab covers the specified distance in meters.
     * Slabs use inclusive boundaries: [min, max].
     */
    public boolean covers(int distanceMeters) {
        return Boolean.TRUE.equals(active)
                && distanceMeters >= minimumDistanceMeters
                && distanceMeters <= maximumDistanceMeters;
    }
}
