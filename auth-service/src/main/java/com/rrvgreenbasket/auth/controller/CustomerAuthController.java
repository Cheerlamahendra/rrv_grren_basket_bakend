package com.rrvgreenbasket.auth.controller;

import com.rrvgreenbasket.auth.constants.AuthConstants;
import com.rrvgreenbasket.auth.dto.request.*;
import com.rrvgreenbasket.auth.dto.response.*;
import com.rrvgreenbasket.auth.service.AuthService;
import com.rrvgreenbasket.auth.service.CustomerEmailAuthService;
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
 * REST Controller for all customer authentication endpoints:
 * <ul>
 *   <li>Legacy: mobile OTP registration, login, token refresh, logout (preserved, unchanged)</li>
 *   <li>New (V3): email+password registration (send-otp / verify-otp), email+password login,
 *       and three-phase forgot-password (send-otp / verify-otp / reset)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auth/customer")
@Tag(name = "Customer Authentication",
     description = "Customer registration, email/password login, forgot-password, token management")
public class CustomerAuthController {

    private static final Logger log = LoggerFactory.getLogger(CustomerAuthController.class);

    private final AuthService authService;
    private final CustomerEmailAuthService customerEmailAuthService;

    public CustomerAuthController(AuthService authService,
                                   CustomerEmailAuthService customerEmailAuthService) {
        this.authService = authService;
        this.customerEmailAuthService = customerEmailAuthService;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

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

    // =========================================================================
    // LEGACY: Mobile OTP Registration (preserved, unchanged)
    // =========================================================================

    @Operation(summary = "[Legacy] Send Mobile Registration OTP",
               description = "Stages registration metadata and dispatches a 6-digit OTP to mobile.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OTP dispatched"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Mobile already registered"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Resend cooldown active")
    })
    @PostMapping("/register/send-otp")
    public ResponseEntity<ApiResponse<OtpResponse>> sendRegistrationOtp(
            @Valid @RequestBody CustomerRegisterSendOtpRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        log.info("REST request for mobile registration OTP: {}", request.getMobileNumber());
        OtpResponse response = authService.sendCustomerRegistrationOtp(request, clientIp);
        return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
    }

    @Operation(summary = "[Legacy] Verify Mobile Registration OTP & Auto-Login",
               description = "Verifies registration OTP, initializes profile in Customer Service, and returns auth tokens.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Registered and authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid or expired OTP"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Profile provisioning failed")
    })
    @PostMapping("/register/verify-otp")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyRegistrationOtp(
            @Valid @RequestBody CustomerRegisterVerifyOtpRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        String userAgent = getUserAgent(httpRequest);
        log.info("REST request to verify mobile registration OTP: {}", request.getMobileNumber());
        AuthResponse response = authService.verifyCustomerRegistrationOtp(request, clientIp, userAgent);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Customer registration successful.", response));
    }

    // =========================================================================
    // NEW (V3): Email Registration – Phase 1: Send OTP
    // =========================================================================

    @Operation(summary = "Email Registration – Send OTP",
               description = "Validates registration data, stages it securely, and sends a 6-digit OTP to the email address. " +
                             "Accepts: fullName, mobileNumber, email, password.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OTP sent to email"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email or mobile already registered"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "OTP resend cooldown active")
    })
    @PostMapping("/email-register/send-otp")
    public ResponseEntity<ApiResponse<OtpResponse>> sendEmailRegistrationOtp(
            @Valid @RequestBody CustomerEmailRegisterSendOtpRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        log.info("REST request for email registration OTP: email={}", "***");
        OtpResponse response = customerEmailAuthService.sendRegistrationOtp(request, clientIp);
        return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
    }

    // =========================================================================
    // NEW (V3): Email Registration – Phase 2: Verify OTP
    // =========================================================================

    @Operation(summary = "Email Registration – Verify OTP & Complete",
               description = "Verifies the email OTP, creates the AuthUser, calls Customer Service to create the profile, " +
                             "activates the account, and returns JWT tokens. No tokens are returned if profile creation fails.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Registration complete, authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid or expired OTP"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email or mobile already registered"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Customer Service profile creation failed")
    })
    @PostMapping("/email-register/verify-otp")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyEmailRegistrationOtp(
            @Valid @RequestBody CustomerEmailRegisterVerifyOtpRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        String userAgent = getUserAgent(httpRequest);
        log.info("REST request to verify email registration OTP");
        AuthResponse response = customerEmailAuthService.verifyRegistrationOtp(request, clientIp, userAgent);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration complete. You are now logged in.", response));
    }

    // =========================================================================
    // NEW (V3): Email + Password Login
    // =========================================================================

    @Operation(summary = "Email & Password Login",
               description = "Authenticates a customer using their email address and password. " +
                             "Returns JWT access and refresh tokens on success. " +
                             "NOTE: Use /email-login (not /login) to avoid conflict with the legacy mobile-OTP login path.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Login successful"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid credentials"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "423", description = "Account blocked, disabled, or pending")
    })
    @PostMapping("/email-login")
    public ResponseEntity<ApiResponse<AuthResponse>> emailPasswordLogin(
            @Valid @RequestBody CustomerEmailPasswordLoginRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        String userAgent = getUserAgent(httpRequest);
        log.info("REST request for email/password login");
        AuthResponse response = customerEmailAuthService.emailPasswordLogin(request, clientIp, userAgent);
        return ResponseEntity.ok(ApiResponse.success("Login successful.", response));
    }

    // =========================================================================
    // LEGACY: Mobile OTP Login (preserved, unchanged)
    // =========================================================================

    @Operation(summary = "[Legacy] Send Mobile Login OTP",
               description = "Dispatches a 6-digit login OTP to a registered customer's mobile.")
    @PostMapping("/login/send-otp")
    public ResponseEntity<ApiResponse<OtpResponse>> sendLoginOtp(
            @Valid @RequestBody CustomerLoginSendOtpRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        OtpResponse response = authService.sendCustomerLoginOtp(request, clientIp);
        return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
    }

    @Operation(summary = "[Legacy] Verify Mobile Login OTP",
               description = "Authenticates customer via mobile OTP and issues session tokens.")
    @PostMapping("/login/verify-otp")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyLoginOtp(
            @Valid @RequestBody CustomerLoginVerifyOtpRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        String userAgent = getUserAgent(httpRequest);
        AuthResponse response = authService.verifyCustomerLoginOtp(request, clientIp, userAgent);
        return ResponseEntity.ok(ApiResponse.success("Login successful.", response));
    }

    // =========================================================================
    // NEW (V3): Forgot Password – Phase 1: Send OTP
    // =========================================================================

    @Operation(summary = "Forgot Password – Send OTP",
               description = "Sends a PASSWORD_RESET OTP to the email address if an active verified account exists. " +
                             "Returns a consistent response regardless of whether the email is registered (anti-enumeration).")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Response sent (consistent regardless of account existence)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error")
    })
    @PostMapping("/forgot-password/send-otp")
    public ResponseEntity<ApiResponse<OtpResponse>> sendForgotPasswordOtp(
            @Valid @RequestBody CustomerForgotPasswordSendOtpRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        log.info("REST request for forgot-password OTP");
        OtpResponse response = customerEmailAuthService.sendForgotPasswordOtp(request, clientIp);
        return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
    }

    // =========================================================================
    // NEW (V3): Forgot Password – Phase 2: Verify OTP
    // =========================================================================

    @Operation(summary = "Forgot Password – Verify OTP",
               description = "Verifies the PASSWORD_RESET OTP and returns a short-lived, single-use reset token. " +
                             "The token expires in 15 minutes.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OTP verified, reset token issued"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid or expired OTP"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No active OTP challenge found")
    })
    @PostMapping("/forgot-password/verify-otp")
    public ResponseEntity<ApiResponse<PasswordResetTokenResponse>> verifyForgotPasswordOtp(
            @Valid @RequestBody CustomerForgotPasswordVerifyOtpRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        log.info("REST request to verify forgot-password OTP");
        PasswordResetTokenResponse response = customerEmailAuthService.verifyForgotPasswordOtp(request, clientIp);
        return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
    }

    // =========================================================================
    // NEW (V3): Forgot Password – Phase 3: Reset Password
    // =========================================================================

    @Operation(summary = "Forgot Password – Reset",
               description = "Resets the customer's password using the short-lived reset token from the previous step. " +
                             "All existing sessions are revoked. The reset token is single-use.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password reset successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Password mismatch or validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid, expired, or already-used reset token"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Password confirmation mismatch")
    })
    @PostMapping("/forgot-password/reset")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody CustomerForgotPasswordResetRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        log.info("REST request to reset password");
        customerEmailAuthService.resetPassword(request, clientIp);
        return ResponseEntity.ok(ApiResponse.success(
                "Password reset successfully. Please log in with your new password.", null));
    }

    // =========================================================================
    // Token Refresh & Logout (preserved, unchanged)
    // =========================================================================

    @Operation(summary = "Refresh JWT Token",
               description = "Rotates the refresh token within the current session family and issues a new access token.")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request) {
        log.info("REST request to refresh JWT token");
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully.", response));
    }

    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Customer Logout",
               description = "Revokes the current device session and refresh token family.")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<CustomerLogoutResponse>> logout(
            @RequestHeader(value = AuthConstants.AUTHORIZATION_HEADER, required = true) String authHeader,
            @Valid @RequestBody CustomerLogoutRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        log.info("REST request for customer logout");
        CustomerLogoutResponse response = authService.logoutCustomer(authHeader, request, clientIp);
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully.", response));
    }
}
