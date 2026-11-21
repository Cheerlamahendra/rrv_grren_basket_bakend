package com.rrvgreenbasket.auth.dto.response;

import lombok.*;

/**
 * PasswordResetTokenResponse
 *
 * <p><strong>What it does:</strong></p>
 * Response returned after a successful PASSWORD_RESET OTP verification.
 * Carries the short-lived, single-use reset authorization token and its expiry.
 *
 * <p><strong>Security note:</strong></p>
 * This token is a UUID-based opaque token. Only the SHA-256 hash is stored
 * in the database. The raw token is returned here exactly once and is never logged.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetTokenResponse {

    private String resetToken;
    private int expiresInMinutes;
    private String message;
}
