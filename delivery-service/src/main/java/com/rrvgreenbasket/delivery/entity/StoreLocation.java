package com.rrvgreenbasket.delivery.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "store_locations",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_store_locations_public_id", columnNames = "public_id")
        },
        indexes = {
                @Index(name = "idx_store_locations_primary", columnList = "primary_store, delivery_enabled"),
                @Index(name = "idx_store_locations_public_id", columnList = "public_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoreLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", length = 36, nullable = false, unique = true, columnDefinition = "CHAR(36)")
    private String publicId;

    @Column(name = "store_name", length = 150, nullable = false)
    private String storeName;

    @Column(name = "google_place_id", length = 255)
    private String googlePlaceId;

    @Column(name = "formatted_address", length = 500, nullable = false)
    private String formattedAddress;

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @Column(name = "city", length = 100, nullable = false)
    private String city;

    @Column(name = "state", length = 100, nullable = false)
    private String state;

    @Column(name = "postal_code", length = 20, nullable = false)
    private String postalCode;

    @Column(name = "country", length = 100, nullable = false)
    @Builder.Default
    private String country = "India";

    @Column(name = "maximum_delivery_distance_meters", nullable = false)
    @Builder.Default
    private Integer maximumDeliveryDistanceMeters = 5000;

    @Column(name = "delivery_enabled", nullable = false)
    @Builder.Default
    private Boolean deliveryEnabled = true;

    @Column(name = "primary_store", nullable = false)
    @Builder.Default
    private Boolean primaryStore = true;

    @Column(name = "minimum_order_amount", precision = 10, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal minimumOrderAmount = BigDecimal.ZERO;

    @Column(name = "free_delivery_threshold", precision = 10, scale = 2)
    private BigDecimal freeDeliveryThreshold;

    @Column(name = "estimated_delivery_time", length = 50, nullable = false)
    @Builder.Default
    private String estimatedDeliveryTime = "30-45 mins";

    @Column(name = "currency", length = 10, nullable = false)
    @Builder.Default
    private String currency = "INR";

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
}
