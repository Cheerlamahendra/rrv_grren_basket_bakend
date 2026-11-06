package com.rrvgreenbasket.auth.controller;

import com.rrvgreenbasket.auth.constants.AuthConstants;
import com.rrvgreenbasket.auth.dto.request.*;
import com.rrvgreenbasket.auth.dto.response.ApiResponse;
import com.rrvgreenbasket.auth.dto.response.AuthResponse;
import com.rrvgreenbasket.auth.dto.response.CustomerLogoutResponse;
import com.rrvgreenbasket.auth.dto.response.OtpResponse;
import com.rrvgreenbasket.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * CustomerAuthController
 *
 * <p><strong>What it does:</strong></p>
 * Dedicated REST Controller for passwordless Customer mobile OTP registration,
 * verification, automatic onboarding, returning customer login, token refresh,
 * and individual device logout.
 */
@RestController
@RequestMapping("/api/auth/customer")
@Tag(name = "Customer Authentication", description = "Passwordless customer registration, login, session, and token management")
public class CustomerAuthController {

    private static final Logger log = LoggerFactory.getLogger(CustomerAuthController.class);

    private final AuthService authService;

    public CustomerAuthController(AuthService authService) {
        this.authService = authService;
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String getUserAgent(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        return (ua != null && !ua.isBlank()) ? ua : "UnknownDevice";
    }

    @Operation(summary = "Send Customer Registration OTP", description = "Stages registration metadata (fullName, email) and dispatches a 6-digit OTP challenge.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Registration OTP dispatched successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid mobile number or full name format"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Mobile number is already registered; instruct user to Login"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Resend cooldown active")
    })
    @PostMapping("/register/send-otp")
    public ResponseEntity<ApiResponse<OtpResponse>> sendRegistrationOtp(
            @Valid @RequestBody CustomerRegisterSendOtpRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        log.info("REST request for customer registration OTP for mobile: {}", request.getMobileNumber());
        OtpResponse response = authService.sendCustomerRegistrationOtp(request, clientIp);
        return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
    }

    @Operation(summary = "Verify Registration OTP & Auto-Login", description = "Verifies registration OTP, initializes profile in Customer Service, and returns authenticated session tokens.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Customer registered and authenticated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid or expired OTP code"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Mobile number already registered"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Customer profile provisioning failed; safe compensation executed")
    })
    @PostMapping("/register/verify-otp")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyRegistrationOtp(
            @Valid @RequestBody CustomerRegisterVerifyOtpRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        String userAgent = getUserAgent(httpRequest);
        log.info("REST request to verify registration OTP for mobile: {}", request.getMobileNumber());
        AuthResponse response = authService.verifyCustomerRegistrationOtp(request, clientIp, userAgent);
        return ResponseEntity.ok(ApiResponse.success("Customer registration successful. Logged in automatically.", response));
    }

    @Operation(summary = "Send Customer Login OTP", description = "Dispatches a 6-digit numeric login OTP challenge to an active registered customer.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Login OTP dispatched successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid mobile number format"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Customer not registered"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Resend cooldown active")
    })
    @PostMapping("/login/send-otp")
    public ResponseEntity<ApiResponse<OtpResponse>> sendLoginOtp(
            @Valid @RequestBody CustomerLoginSendOtpRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        log.info("REST request for customer login OTP for mobile: {}", request.getMobileNumber());
        OtpResponse response = authService.sendCustomerLoginOtp(request, clientIp);
        return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
    }

    @Operation(summary = "Verify Customer Login OTP", description = "Authenticates returning customer, spawns an independent device session, and issues tokens.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Customer authenticated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid or expired OTP code"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Account blocked or disabled"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Customer not registered")
    })
    @PostMapping("/login/verify-otp")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyLoginOtp(
            @Valid @RequestBody CustomerLoginVerifyOtpRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        String userAgent = getUserAgent(httpRequest);
        log.info("REST request to verify customer login OTP for mobile: {}", request.getMobileNumber());
        AuthResponse response = authService.verifyCustomerLoginOtp(request, clientIp, userAgent);
        return ResponseEntity.ok(ApiResponse.success("Customer login successful", response));
    }

    @Operation(summary = "Customer Token Refresh", description = "Rotates refresh token within the current session family and issues a new access token.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Token refreshed successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Refresh token expired, invalid, or revoked")
    })
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("REST request to refresh customer JWT token");
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", response));
    }

    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Customer Device Logout", description = "Revokes current device session and refresh token family. Old access token is immediately invalidated.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Customer logged out successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid access token")
    })
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<CustomerLogoutResponse>> logout(
            @RequestHeader(value = AuthConstants.AUTHORIZATION_HEADER, required = true) String authHeader,
            @Valid @RequestBody CustomerLogoutRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        log.info("REST request for customer device logout");
        CustomerLogoutResponse response = authService.logoutCustomer(authHeader, request, clientIp);
        return ResponseEntity.ok(ApiResponse.success("Customer logged out successfully", response));
    }
}
