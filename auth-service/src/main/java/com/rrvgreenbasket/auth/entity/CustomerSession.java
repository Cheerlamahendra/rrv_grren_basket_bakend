package com.rrvgreenbasket.auth.entity;

import com.rrvgreenbasket.auth.enums.SessionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * CustomerSession Entity
 *
 * <p><strong>What it does:</strong></p>
 * Tracks independent client device sessions for authenticated customers.
 * Backed by MySQL table <code>customer_sessions</code>.
 * Provides the authoritative state for immediate access token revocation and multi-device isolation.
 */
@Entity
@Table(
    name = "customer_sessions",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_customer_sessions_session_id", columnNames = "session_id")
    },
    indexes = {
        @Index(name = "idx_customer_sessions_user_status", columnList = "user_id, status"),
        @Index(name = "idx_customer_sessions_status", columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "session_id", nullable = false, unique = true, columnDefinition = "char(36)")
    private String sessionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AuthUser authUser;

    @Column(name = "device_info")
    private String deviceInfo;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SessionStatus status = SessionStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_active_at", nullable = false)
    private LocalDateTime lastActiveAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revocation_reason", length = 100)
    private String revocationReason;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (lastActiveAt == null) {
            lastActiveAt = LocalDateTime.now();
        }
        if (status == null) {
            status = SessionStatus.ACTIVE;
        }
    }
}
