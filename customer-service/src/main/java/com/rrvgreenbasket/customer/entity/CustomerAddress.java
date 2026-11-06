package com.rrvgreenbasket.customer.entity;

import com.rrvgreenbasket.customer.enums.AddressType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CustomerAddress Entity
 *
 * <p><strong>What it does:</strong></p>
 * Represents an individual delivery address associated with a customer.
 * Mapped to MySQL table <code>customer_addresses</code> in <code>customer_db</code>.
 *
 * <p><strong>Business Rules:</strong></p>
 * <ul>
 *   <li>Only one address per customer may be designated as default at any given time.</li>
 *   <li>Addresses are cascade-deleted if the parent customer profile is removed.</li>
 *   <li>Exposes a UUID <code>publicId</code> to external consumers to prevent sequential ID harvesting.</li>
 * </ul>
 */
@Entity
@Table(
    name = "customer_addresses",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_customer_addresses_public_id", columnNames = "public_id")
    },
    indexes = {
        @Index(name = "idx_customer_addresses_customer_id", columnList = "customer_id"),
        @Index(name = "idx_customer_addresses_default", columnList = "customer_id, is_default")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, columnDefinition = "char(36)")
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, foreignKey = @ForeignKey(name = "fk_customer_addresses_customer"))
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "address_type", nullable = false, length = 20)
    @Builder.Default
    private AddressType addressType = AddressType.HOME;

    @Column(name = "receiver_name", nullable = false, length = 150)
    private String receiverName;

    @Column(name = "receiver_mobile", nullable = false, length = 20)
    private String receiverMobile;

    @Column(name = "address_line1", nullable = false, length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(name = "landmark", length = 150)
    private String landmark;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "state", nullable = false, length = 100)
    private String state;

    @Column(name = "pincode", nullable = false, length = 20)
    private String pincode;

    @Column(name = "country", nullable = false, length = 100)
    @Builder.Default
    private String country = "India";

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean isDefault = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.publicId == null) {
            this.publicId = UUID.randomUUID().toString();
        }
        if (this.country == null || this.country.isBlank()) {
            this.country = "India";
        }
        if (this.addressType == null) {
            this.addressType = AddressType.HOME;
        }
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
