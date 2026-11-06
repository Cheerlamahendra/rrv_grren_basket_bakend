package com.rrvgreenbasket.auth.service;

import com.rrvgreenbasket.auth.dto.request.*;
import com.rrvgreenbasket.auth.dto.response.AuthResponse;
import com.rrvgreenbasket.auth.dto.response.CustomerLogoutResponse;
import com.rrvgreenbasket.auth.dto.response.OtpResponse;
import com.rrvgreenbasket.auth.dto.response.UserSummaryResponse;

/**
 * AuthService Interface
 *
 * <p><strong>What it does:</strong></p>
 * Central business service contract for Customer passwordless authentication,
 * Registration Saga, multi-device session management, Admin BCrypt auth, and token refresh.
 */
public interface AuthService {

    // Preserved Legacy Endpoints
    OtpResponse requestOtp(SendOtpRequest request, String clientIp);

    boolean verifyOtpOnly(VerifyOtpRequest request, String clientIp);

    AuthResponse verifyOtpAndAuthenticate(VerifyOtpRequest request, String clientIp, String userAgent);

    AuthResponse registerCustomer(CustomerRegisterRequest request, String clientIp);

    AuthResponse loginCustomer(CustomerLoginRequest request, String clientIp);

    AuthResponse loginAdmin(AdminLoginRequest request, String clientIp);

    AuthResponse refreshToken(RefreshTokenRequest request);

    void logout(String authorizationHeader, String refreshToken);

    UserSummaryResponse getCurrentUser(String identifier);

    // Dedicated Customer Endpoints
    OtpResponse sendCustomerRegistrationOtp(CustomerRegisterSendOtpRequest request, String clientIp);

    AuthResponse verifyCustomerRegistrationOtp(CustomerRegisterVerifyOtpRequest request, String clientIp, String userAgent);

    OtpResponse sendCustomerLoginOtp(CustomerLoginSendOtpRequest request, String clientIp);

    AuthResponse verifyCustomerLoginOtp(CustomerLoginVerifyOtpRequest request, String clientIp, String userAgent);

    CustomerLogoutResponse logoutCustomer(String bearerToken, CustomerLogoutRequest request, String clientIp);
}
