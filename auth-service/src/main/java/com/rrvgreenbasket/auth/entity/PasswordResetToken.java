package com.rrvgreenbasket.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * PasswordResetToken Entity
 *
 * <p><strong>What it does:</strong></p>
 * Short-lived, single-use authorization token issued after a customer successfully
 * verifies a PASSWORD_RESET OTP challenge. Stored as a SHA-256 hash to prevent
 * disclosure attacks.
 *
 * <p><strong>Constraints:</strong></p>
 * <ul>
 *   <li><code>uk_prt_token_hash</code>: Unique token hash.</li>
 *   <li><code>fk_prt_user</code>: Foreign key to <code>auth_users(id)</code>.</li>
 * </ul>
 *
 * <p><strong>Security note:</strong></p>
 * The raw token is never stored. Only the SHA-256 hex digest is persisted.
 */
@Entity
@Table(
    name = "password_reset_tokens",
    indexes = {
        @Index(name = "idx_prt_user_id", columnList = "user_id"),
        @Index(name = "idx_prt_expires_at", columnList = "expires_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AuthUser authUser;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
