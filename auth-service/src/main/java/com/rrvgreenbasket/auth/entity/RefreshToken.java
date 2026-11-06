package com.rrvgreenbasket.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * RefreshToken Entity
 *
 * <p><strong>What it does:</strong></p>
 * Manages refresh token lifecycle and family-based rotation tracking
 * mapped to MySQL table <code>refresh_tokens</code>.
 *
 * <p><strong>Constraints:</strong></p>
 * <ul>
 *   <li><code>uk_refresh_tokens_token_hash</code>: Unique constraint on 64-character SHA-256 digest.</li>
 *   <li><code>fk_refresh_tokens_user</code>: Foreign key to <code>auth_users(id)</code>.</li>
 *   <li><code>fk_refresh_tokens_replaced_by</code>: Self-referencing FK tracking replacement token.</li>
 * </ul>
 */
@Entity
@Table(
    name = "refresh_tokens",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_refresh_tokens_token_hash", columnNames = "token_hash")
    },
    indexes = {
        @Index(name = "idx_refresh_tokens_user_id", columnList = "user_id"),
        @Index(name = "idx_refresh_tokens_family_id", columnList = "token_family_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AuthUser authUser;

    @Column(name = "token_hash", nullable = false, unique = true, columnDefinition = "char(64)")
    private String tokenHash;

    @Column(name = "token_family_id", nullable = false, columnDefinition = "char(36)")
    private String tokenFamilyId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaced_by_token_id")
    private RefreshToken replacedByToken;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
