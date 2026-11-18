package com.rrvgreenbasket.delivery.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "customer_delivery_addresses",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_customer_delivery_addresses_public_id", columnNames = "public_id")
        },
        indexes = {
                @Index(name = "idx_cust_addr_customer_id", columnList = "customer_id"),
                @Index(name = "idx_cust_addr_public_id", columnList = "public_id"),
                @Index(name = "idx_cust_addr_default", columnList = "customer_id, is_default")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerDeliveryAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", length = 36, nullable = false, unique = true, columnDefinition = "CHAR(36)")
    private String publicId;

    @Column(name = "customer_id", length = 100, nullable = false)
    private String customerId;

    @Column(name = "google_place_id", length = 255, nullable = false)
    private String googlePlaceId;

    @Column(name = "formatted_address", length = 500, nullable = false)
    private String formattedAddress;

    @Column(name = "address_line", length = 255)
    private String addressLine;

    @Column(name = "landmark", length = 150)
    private String landmark;

    @Column(name = "city", length = 100, nullable = false)
    private String city;

    @Column(name = "state", length = 100, nullable = false)
    private String state;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "country", length = 100, nullable = false)
    @Builder.Default
    private String country = "India";

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    @Column(name = "verified", nullable = false)
    @Builder.Default
    private Boolean verified = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;
}
