package com.rrvgreenbasket.customer.entity;

import com.rrvgreenbasket.customer.enums.AccountStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Customer Entity
 *
 * <p><strong>What it does:</strong></p>
 * Core domain entity representing a registered customer profile in RRV Green Basket.
 * Mapped to MySQL table <code>customers</code> in <code>customer_db</code>.
 *
 * <p><strong>Why it is needed:</strong></p>
 * Decouples profile and personal delivery details from authentication credentials.
 * The <code>authUserId</code> links to the external Auth microservice user without
 * maintaining cross-database foreign keys.
 *
 * <p><strong>Key Attributes:</strong></p>
 * <ul>
 *   <li><code>publicId</code>: UUID exposed across REST APIs.</li>
 *   <li><code>authUserId</code>: Unique identifier matching Auth Service's public ID.</li>
 *   <li><code>status</code>: ACTIVE, INACTIVE, or SUSPENDED.</li>
 * </ul>
 */
@Entity
@Table(
    name = "customers",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_customers_public_id", columnNames = "public_id"),
        @UniqueConstraint(name = "uk_customers_auth_user_id", columnNames = "auth_user_id"),
        @UniqueConstraint(name = "uk_customers_mobile", columnNames = "mobile_number")
    },
    indexes = {
        @Index(name = "idx_customers_email", columnList = "email"),
        @Index(name = "idx_customers_status", columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, columnDefinition = "char(36)")
    private String publicId;

    @Column(name = "auth_user_id", nullable = false, unique = true, columnDefinition = "char(36)")
    private String authUserId;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "mobile_number", nullable = false, length = 20)
    private String mobileNumber;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    @OrderBy("isDefault DESC, createdAt DESC")
    private List<CustomerAddress> addresses = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.publicId == null) {
            this.publicId = UUID.randomUUID().toString();
        }
        if (this.status == null) {
            this.status = AccountStatus.ACTIVE;
        }
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void addAddress(CustomerAddress address) {
        addresses.add(address);
        address.setCustomer(this);
    }

    public void removeAddress(CustomerAddress address) {
        addresses.remove(address);
        address.setCustomer(null);
    }
}
