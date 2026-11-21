package com.rrvgreenbasket.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

/**
 * CustomerEmailRegisterSendOtpRequest
 *
 * <p><strong>What it does:</strong></p>
 * Inbound payload for Phase 1 of email-based customer registration.
 * Triggers OTP generation and delivery via Resend to the provided email address.
 * Password is validated and hashed here; the hash is staged in the OTP challenge
 * record until OTP verification succeeds.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerEmailRegisterSendOtpRequest {

    @Schema(description = "Customer full name", example = "Mahendra Kumar",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Full name is required for registration")
    @Size(min = 2, max = 150, message = "Full name must be between 2 and 150 characters")
    private String fullName;

    @Schema(description = "Customer mobile number (10 digits or E.164 format)",
            example = "9876543210", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^(\\+91)?[6-9]\\d{9}$",
             message = "Mobile number must be a valid 10-digit Indian number, optionally prefixed with +91")
    private String mobileNumber;

    @Schema(description = "Email address – will receive the OTP",
            example = "mahendra@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Email address is required")
    @Email(message = "Email must be a valid email address")
    @Size(max = 254, message = "Email cannot exceed 254 characters")
    private String email;

    @Schema(description = "Password (min 8 chars, must include uppercase, lowercase, digit and special character)",
            example = "StrongPassword@123", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&_#^()\\-+=])[A-Za-z\\d@$!%*?&_#^()\\-+=]{8,}$",
        message = "Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character"
    )
    private String password;
}
