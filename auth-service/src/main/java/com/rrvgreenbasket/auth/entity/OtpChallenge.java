package com.rrvgreenbasket.auth.entity;

import com.rrvgreenbasket.auth.enums.OtpPurpose;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * OtpChallenge Entity
 *
 * <p><strong>What it does:</strong></p>
 * Manages OTP generation, attempt throttling, and validation records
 * mapped to MySQL table <code>otp_challenges</code>.
 * V3 adds <code>pending_mobile_number</code> and <code>pending_password_hash</code>
 * for staging email-based registration data until OTP is verified.
 *
 * <p><strong>Constraints:</strong></p>
 * <ul>
 *   <li><code>chk_otp_attempts</code>: attempts &lt;= max_attempts.</li>
 *   <li><code>chk_otp_purpose</code>: purpose IN ('REGISTRATION', 'LOGIN', 'MOBILE_CHANGE', 'PASSWORD_RESET').</li>
 * </ul>
 */
@Entity
@Table(
    name = "otp_challenges",
    indexes = {
        @Index(name = "idx_otp_mobile_purpose", columnList = "mobile_number, purpose"),
        @Index(name = "idx_otp_expires_at", columnList = "expires_at")
    }
)
@SuppressWarnings("java:S2160") // Lombok equals/hashCode on @Entity is intentional
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "mobile_number", nullable = true, length = 15)
    private String mobileNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private OtpPurpose purpose;

    @Column(name = "otp_hash", nullable = false, length = 255)
    private String otpHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private int maxAttempts = 5;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "requested_ip", length = 45)
    private String requestedIp;

    @Column(name = "full_name", length = 150)
    private String fullName;

    @Column(name = "email", length = 254)
    private String email;

    /**
     * Staged mobile number for email-based registration.
     * Stored here to avoid a separate pending_registrations table.
     * Populated during send-otp phase; consumed and cleared on verify-otp.
     */
    @Column(name = "pending_mobile_number", length = 15)
    private String pendingMobileNumber;

    /**
     * BCrypt hash of the submitted password, staged until OTP verification.
     * Never returned in any API response. Cleared after consumption.
     * Security: Only the hash is stored, never the plaintext password.
     */
    @Column(name = "pending_password_hash", length = 255)
    private String pendingPasswordHash;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
