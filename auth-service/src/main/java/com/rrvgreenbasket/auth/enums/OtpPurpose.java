package com.rrvgreenbasket.auth.enums;

/**
 * OtpPurpose
 *
 * <p><strong>What it does:</strong></p>
 * Database OTP purpose enum enforced by MySQL CHECK constraint (updated in V3 migration):
 * <code>chk_otp_purpose CHECK (purpose IN ('REGISTRATION', 'LOGIN', 'MOBILE_CHANGE', 'PASSWORD_RESET'))</code>
 */
public enum OtpPurpose {
    REGISTRATION,
    LOGIN,
    MOBILE_CHANGE,
    PASSWORD_RESET
}
