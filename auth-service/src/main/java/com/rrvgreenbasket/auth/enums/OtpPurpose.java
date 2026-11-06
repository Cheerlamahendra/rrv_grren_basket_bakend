package com.rrvgreenbasket.auth.enums;

/**
 * OtpPurpose
 *
 * <p><strong>What it does:</strong></p>
 * Database OTP purpose enum enforced by MySQL CHECK constraint:
 * <code>chk_otp_purpose CHECK (purpose IN ('REGISTRATION', 'LOGIN', 'MOBILE_CHANGE'))</code>
 */
public enum OtpPurpose {
    REGISTRATION,
    LOGIN,
    MOBILE_CHANGE
}
