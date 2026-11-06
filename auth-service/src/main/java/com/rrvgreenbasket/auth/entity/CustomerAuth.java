package com.rrvgreenbasket.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * CustomerAuth Entity
 *
 * <p><strong>What it does:</strong></p>
 * Customer credential extension entity mapped to MySQL table <code>customer_auth</code>.
 *
 * <p><strong>Constraints:</strong></p>
 * <ul>
 *   <li><code>uk_customer_auth_mobile</code>: Unique constraint on mobile_number.</li>
 *   <li><code>fk_customer_auth_user</code>: Foreign key to <code>auth_users(id)</code>.</li>
 * </ul>
 */
@Entity
@Table(
    name = "customer_auth",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_customer_auth_mobile", columnNames = "mobile_number")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerAuth {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id", nullable = false)
    private AuthUser user;

    @Column(name = "mobile_number", nullable = false, length = 15)
    private String mobileNumber;

    @Column(name = "mobile_verified", nullable = false)
    @Builder.Default
    private boolean mobileVerified = false;

    @Column(name = "mobile_verified_at")
    private LocalDateTime mobileVerifiedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
