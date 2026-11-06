package com.rrvgreenbasket.auth.entity;

import com.rrvgreenbasket.auth.enums.UserRole;
import com.rrvgreenbasket.auth.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * AuthUser Entity
 *
 * <p><strong>What it does:</strong></p>
 * Core identity entity mapped to MySQL table <code>auth_users</code>.
 *
 * <p><strong>Constraints:</strong></p>
 * <ul>
 *   <li><code>uk_auth_users_public_id</code>: Unique public UUID.</li>
 *   <li><code>chk_auth_users_role</code>: Role must be in ('USER', 'ADMIN').</li>
 *   <li><code>chk_auth_users_status</code>: Status must be in ('PENDING', 'ACTIVE', 'BLOCKED', 'DISABLED').</li>
 * </ul>
 */
@Entity
@Table(
    name = "auth_users",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_auth_users_public_id", columnNames = "public_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, columnDefinition = "char(36)")
    private String publicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private CustomerAuth customerAuth;

    @OneToOne(mappedBy = "authUser", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private AdminAuth adminAuth;

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
