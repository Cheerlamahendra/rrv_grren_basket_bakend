package com.rrvgreenbasket.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * AuthAuditEvent Entity
 *
 * <p><strong>What it does:</strong></p>
 * Security audit trail recording login, OTP, and token lifecycle events
 * mapped to MySQL table <code>auth_audit_events</code>.
 *
 * <p><strong>Constraints:</strong></p>
 * <ul>
 *   <li><code>fk_auth_audit_user</code>: Foreign key to <code>auth_users(id)</code> with ON DELETE SET NULL.</li>
 * </ul>
 */
@Entity
@Table(
    name = "auth_audit_events",
    indexes = {
        @Index(name = "idx_auth_audit_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_auth_audit_event_created", columnList = "event_type, created_at"),
        @Index(name = "idx_auth_audit_session", columnList = "session_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AuthUser user;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "success", nullable = false)
    private Boolean success;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "session_id", length = 36, columnDefinition = "char(36)")
    private String sessionId;

    @Column(name = "details", length = 500)
    private String details;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
