package com.rrvgreenbasket.auth.service;

import com.rrvgreenbasket.auth.dto.request.*;
import com.rrvgreenbasket.auth.dto.response.AuthResponse;
import com.rrvgreenbasket.auth.dto.response.OtpResponse;
import com.rrvgreenbasket.auth.dto.response.PasswordResetTokenResponse;

/**
 * CustomerEmailAuthService
 *
 * <p><strong>What it does:</strong></p>
 * Business service contract for all email-based customer authentication workflows:
 * <ul>
 *   <li>Two-phase email OTP registration (send-otp → verify-otp → JWT tokens)</li>
 *   <li>Email + password login</li>
 *   <li>Three-phase forgot-password (send-otp → verify-otp → reset password)</li>
 * </ul>
 *
 * <p><strong>Architecture note:</strong></p>
 * This service is intentionally separate from {@link AuthService} to keep the new
 * email-based flows isolated from the legacy mobile OTP flows. Both coexist.
 */
public interface CustomerEmailAuthService {

    // --- Registration ---
    OtpResponse sendRegistrationOtp(CustomerEmailRegisterSendOtpRequest request, String clientIp);

    AuthResponse verifyRegistrationOtp(CustomerEmailRegisterVerifyOtpRequest request, String clientIp, String userAgent);

    // --- Login ---
    AuthResponse emailPasswordLogin(CustomerEmailPasswordLoginRequest request, String clientIp, String userAgent);

    // --- Forgot Password ---
    OtpResponse sendForgotPasswordOtp(CustomerForgotPasswordSendOtpRequest request, String clientIp);

    PasswordResetTokenResponse verifyForgotPasswordOtp(CustomerForgotPasswordVerifyOtpRequest request, String clientIp);

    void resetPassword(CustomerForgotPasswordResetRequest request, String clientIp);
}
