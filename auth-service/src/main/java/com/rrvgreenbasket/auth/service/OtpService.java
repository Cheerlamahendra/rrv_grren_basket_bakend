package com.rrvgreenbasket.auth.service;

import com.rrvgreenbasket.auth.dto.response.OtpResponse;
import com.rrvgreenbasket.auth.entity.OtpChallenge;
import com.rrvgreenbasket.auth.enums.OtpPurpose;

import java.util.Optional;

/**
 * OtpService Interface
 *
 * <p><strong>What it does:</strong></p>
 * Manages OTP challenges in <code>otp_challenges</code> table, including BCrypt hashing,
 * cooldown enforcement, attempt throttling, metadata staging, and verification.
 */
public interface OtpService {

    OtpResponse generateAndSendOtp(String mobileNumber, OtpPurpose purpose, String clientIp);

    OtpResponse generateAndSendOtp(String mobileNumber, OtpPurpose purpose, String clientIp, String fullName, String email);

    boolean verifyOtp(String mobileNumber, String rawOtp, OtpPurpose purpose, String clientIp);

    Optional<OtpChallenge> getActiveChallenge(String mobileNumber, OtpPurpose purpose);

    void resetAttempts(String mobileNumber, OtpPurpose purpose);
}
