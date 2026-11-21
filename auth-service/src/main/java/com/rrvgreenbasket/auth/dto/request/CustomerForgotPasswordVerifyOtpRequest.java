package com.rrvgreenbasket.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

/**
 * CustomerForgotPasswordVerifyOtpRequest
 *
 * <p><strong>What it does:</strong></p>
 * Inbound payload for Phase 2 of the forgot-password flow.
 * Verifies the PASSWORD_RESET OTP and issues a short-lived reset authorization token.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerForgotPasswordVerifyOtpRequest {

    @Schema(description = "Customer email address",
            example = "mahendra@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Email address is required")
    @Email(message = "Email must be a valid email address")
    private String email;

    @Schema(description = "Six-digit numeric OTP code received via email",
            example = "482910", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "OTP code is required")
    @Pattern(regexp = "^\\d{6}$", message = "OTP must be exactly 6 numeric digits")
    private String otp;
}
