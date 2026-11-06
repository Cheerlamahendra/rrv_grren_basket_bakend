package com.rrvgreenbasket.auth.controller;

import com.rrvgreenbasket.auth.constants.AuthConstants;
import com.rrvgreenbasket.auth.dto.request.*;
import com.rrvgreenbasket.auth.dto.response.ApiResponse;
import com.rrvgreenbasket.auth.dto.response.AuthResponse;
import com.rrvgreenbasket.auth.dto.response.OtpResponse;
import com.rrvgreenbasket.auth.dto.response.UserSummaryResponse;
import com.rrvgreenbasket.auth.enums.OtpPurpose;
import com.rrvgreenbasket.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * AuthController
 *
 * <p><strong>What it does:</strong></p>
 * REST Controller exposing Customer OTP verification, Admin credential login,
 * token refresh, and identity inspection endpoints.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication & Authorization", description = "Endpoints for Customer OTP auth, Admin login, and JWT token management")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    public AuthController(AuthService authService) {
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
        return (ua != null && !ua.isBlank()) ? ua : "WebClient";
    }

    @Operation(summary = "Request Customer Email/Mobile OTP", description = "Generates and dispatches a 6-digit numeric OTP to the recipient.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OTP generated and dispatched successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request format or missing destination"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Customer not found (for login purpose)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Customer already registered (for registration purpose)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Resend cooldown period is still active")
    })
    @PostMapping("/customer/send-otp")
    public ResponseEntity<ApiResponse<OtpResponse>> sendOtp(@Valid @RequestBody SendOtpRequest request, HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        log.info("REST request to send OTP to destination: {}", request.resolveDestination());
        OtpResponse response = authService.requestOtp(request, clientIp);
        return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
    }

    @Operation(summary = "Verify OTP & Authenticate", description = "Validates an OTP. Completes registration or login, provisions customer profile, and returns tokens.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OTP verified successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid or expired OTP code")
    })
    @PostMapping("/customer/verify-otp")
    public ResponseEntity<ApiResponse<?>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request, HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        String userAgent = getUserAgent(httpRequest);
        log.info("REST request to verify OTP for destination: {}", request.resolveDestination());
        if (request.getPurpose() == OtpPurpose.MOBILE_CHANGE) {
            boolean isValid = authService.verifyOtpOnly(request, clientIp);
            return ResponseEntity.ok(ApiResponse.success("OTP verified successfully", isValid));
        }
        AuthResponse response = authService.verifyOtpAndAuthenticate(request, clientIp, userAgent);
        return ResponseEntity.ok(ApiResponse.success("OTP verified successfully", response));
    }

    @Operation(summary = "Register New Customer", description = "Onboards a new customer account using verified OTP.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Customer registered and authenticated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid input or OTP failure"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Mobile number already registered")
    })
    @PostMapping("/customer/register")
    public ResponseEntity<ApiResponse<AuthResponse>> registerCustomer(@Valid @RequestBody CustomerRegisterRequest request, HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        log.info("REST request to register new customer with mobile: {}", request.getMobileNumber());
        AuthResponse response = authService.registerCustomer(request, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Customer registration successful", response));
    }

    @Operation(summary = "Customer Login via OTP", description = "Authenticates an existing customer via 6-digit OTP.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Customer authenticated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid or expired OTP code"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No customer found with provided identifier")
    })
    @PostMapping("/customer/login")
    public ResponseEntity<ApiResponse<AuthResponse>> loginCustomer(@Valid @RequestBody CustomerLoginRequest request, HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        log.info("REST request for customer login with destination: {}", request.resolveDestination());
        AuthResponse response = authService.loginCustomer(request, clientIp);
        return ResponseEntity.ok(ApiResponse.success("Customer login successful", response));
    }

    @Operation(summary = "Admin Login", description = "Authenticates platform administrators using email and BCrypt password.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Admin authenticated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid admin credentials"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "423", description = "Admin account is temporarily locked")
    })
    @PostMapping("/admin/login")
    public ResponseEntity<ApiResponse<AuthResponse>> loginAdmin(@Valid @RequestBody AdminLoginRequest request, HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        log.info("REST request for administrative login for email: {}", request.getEmail());
        AuthResponse response = authService.loginAdmin(request, clientIp);
        return ResponseEntity.ok(ApiResponse.success("Admin login successful", response));
    }

    @Operation(summary = "Rotate Refresh Token", description = "Exchanges a valid refresh token for a new access token and rotated refresh token.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Token rotated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Refresh token expired, invalid, or revoked")
    })
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("REST request to refresh JWT access token");
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", response));
    }

    @Operation(summary = "Logout & Revoke Tokens", description = "Revokes refresh token.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tokens revoked successfully")
    })
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Parameter(hidden = true) @RequestHeader(value = AuthConstants.AUTHORIZATION_HEADER, required = false) String authHeader,
            @RequestParam(value = "refreshToken", required = false) String refreshToken) {
        log.info("REST request to logout user");
        authService.logout(authHeader, refreshToken);
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully. Tokens have been revoked."));
    }

    @Operation(summary = "Get Authenticated User Profile", description = "Returns the profile summary of the currently authenticated token bearer.")
    @SecurityRequirement(name = "BearerAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized or missing token")
    })
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserSummaryResponse>> getProfile(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized"));
        }
        log.info("REST request to fetch profile for user: {}", userDetails.getUsername());
        UserSummaryResponse response = authService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Profile retrieved successfully", response));
    }
}
