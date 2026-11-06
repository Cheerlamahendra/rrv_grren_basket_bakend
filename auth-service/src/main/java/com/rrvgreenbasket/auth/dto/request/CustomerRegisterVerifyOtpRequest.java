package com.rrvgreenbasket.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CustomerRegisterVerifyOtpRequest
 *
 * <p><strong>What it does:</strong></p>
 * Inbound payload verifying registration OTP challenge and executing atomic onboarding.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerRegisterVerifyOtpRequest {

    @Schema(description = "Customer mobile number", example = "+919876500001", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Mobile number is required")
    private String mobileNumber;

    @Schema(description = "Six-digit numeric OTP code", example = "482910", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "OTP code is required")
    @Pattern(regexp = "^\\d{6}$", message = "OTP must be exactly 6 numeric digits")
    private String otp;

    @Schema(description = "Optional fallback full name if not captured during challenge creation", example = "Mahendra Kumar")
    private String fullName;

    @Schema(description = "Optional fallback email if not captured during challenge creation", example = "mahendra@example.com")
    @Email(message = "Invalid email format")
    private String email;
}
