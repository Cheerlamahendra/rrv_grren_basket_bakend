package com.rrvgreenbasket.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OtpResponse
 *
 * <p><strong>What it does:</strong></p>
 * Non-sensitive metadata returned to clients after an OTP is generated and dispatched.
 *
 * <p><strong>Security Note:</strong></p>
 * Plaintext OTP codes are NEVER included in response payloads.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpResponse {

    private String email;
    private int expiresInMinutes;
    private int cooldownSeconds;
    private String message;
}
