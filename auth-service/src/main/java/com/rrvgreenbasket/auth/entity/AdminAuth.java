package com.rrvgreenbasket.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * AdminAuth Entity
 *
 * <p><strong>What it does:</strong></p>
 * Administrator credentials extension entity mapped to MySQL table <code>admin_auth</code>.
 *
 * <p><strong>Constraints:</strong></p>
 * <ul>
 *   <li><code>uk_admin_auth_email</code>: Unique constraint on email.</li>
 *   <li><code>fk_admin_auth_user</code>: Foreign key to <code>auth_users(id)</code>.</li>
 * </ul>
 */
@Entity
@Table(
    name = "admin_auth",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_admin_auth_email", columnNames = "email")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAuth {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id", nullable = false)
    private AuthUser authUser;

    @Column(name = "email", nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "failed_login_attempts", nullable = false)
    @Builder.Default
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

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
