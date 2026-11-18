package com.rrvgreenbasket.delivery.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "delivery_zones",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_delivery_zones_public_id", columnNames = "public_id"),
                @UniqueConstraint(name = "uk_delivery_zones_zone_code", columnNames = "zone_code")
        },
        indexes = {
                @Index(name = "idx_delivery_zones_active", columnList = "active"),
                @Index(name = "idx_delivery_zones_city", columnList = "city, state"),
                @Index(name = "idx_delivery_zones_code", columnList = "zone_code")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryZone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", length = 36, nullable = false, unique = true, columnDefinition = "CHAR(36)")
    private String publicId;

    @Column(name = "zone_name", length = 100, nullable = false)
    private String zoneName;

    @Column(name = "zone_code", length = 50, nullable = false, unique = true)
    private String zoneCode;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "city", length = 100, nullable = false)
    private String city;

    @Column(name = "state", length = 100, nullable = false)
    private String state;

    @Column(name = "pincode", length = 10)
    private String pincode;

    @Column(name = "boundary_geojson", columnDefinition = "LONGTEXT", nullable = false)
    private String boundaryGeojson;

    @Column(name = "delivery_charge", precision = 10, scale = 2, nullable = false)
    private BigDecimal deliveryCharge;

    @Column(name = "minimum_order_amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal minimumOrderAmount;

    @Column(name = "free_delivery_threshold", precision = 10, scale = 2)
    private BigDecimal freeDeliveryThreshold;

    @Column(name = "estimated_delivery_time", length = 50, nullable = false)
    @Builder.Default
    private String estimatedDeliveryTime = "NEXT_DAY";

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private Boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "delivery_zone_pincodes",
            joinColumns = @JoinColumn(name = "zone_id", referencedColumnName = "id", foreignKey = @ForeignKey(name = "fk_delivery_zone_pincodes_zone"))
    )
    @Column(name = "pincode", length = 10, nullable = false)
    @Builder.Default
    private java.util.Set<String> pincodes = new java.util.HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    /**
     * Determines whether this delivery zone restricts delivery by PIN code.
     *
     * @return true if one or more PIN codes are configured for this zone.
     */
    public boolean hasPincodeRestriction() {
        boolean hasSinglePin = pincode != null && !pincode.isBlank();
        boolean hasCollectionPins = pincodes != null && !pincodes.isEmpty();
        return hasSinglePin || hasCollectionPins;
    }

    /**
     * Checks if a specific PIN code is supported by this delivery zone.
     * If no PIN code restriction is configured, returns true.
     *
     * @param targetPincode the normalized PIN code to test
     * @return true if supported or unrestricted
     */
    public boolean supportsPincode(String targetPincode) {
        if (!hasPincodeRestriction()) {
            return true;
        }
        if (targetPincode == null || targetPincode.isBlank()) {
            return false;
        }
        String cleanPin = targetPincode.trim();
        if (pincode != null && pincode.trim().equalsIgnoreCase(cleanPin)) {
            return true;
        }
        if (pincodes != null) {
            for (String p : pincodes) {
                if (p != null && p.trim().equalsIgnoreCase(cleanPin)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Retrieves all effective PIN codes configured for this zone.
     */
    public java.util.Set<String> getAllPincodes() {
        java.util.Set<String> all = new java.util.HashSet<>();
        if (pincode != null && !pincode.isBlank()) {
            all.add(pincode.trim());
        }
        if (pincodes != null) {
            for (String p : pincodes) {
                if (p != null && !p.isBlank()) {
                    all.add(p.trim());
                }
            }
        }
        return all;
    }
}
