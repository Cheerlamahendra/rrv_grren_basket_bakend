package com.rrvgreenbasket.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CustomerRegisterRequest
 *
 * <p><strong>What it does:</strong></p>
 * Payload for onboarding a new customer account with verified OTP.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerRegisterRequest {

    @Schema(description = "Customer mobile number", example = "+918247329044", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Mobile number is required")
    private String mobileNumber;

    @Schema(description = "Customer email address (optional)", example = "priya.sharma@example.com")
    private String email;

    @Schema(description = "Customer full name", example = "Priya Sharma")
    private String fullName;

    @Schema(description = "Six-digit numeric OTP code", example = "482910", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "OTP code is required for registration verification")
    @Pattern(regexp = "^\\d{6}$", message = "OTP must be exactly 6 numeric digits")
    private String otp;
}
