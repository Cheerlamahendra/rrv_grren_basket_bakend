package com.rrvgreenbasket.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

/**
 * CustomerEmailRegisterVerifyOtpRequest
 *
 * <p><strong>What it does:</strong></p>
 * Inbound payload for Phase 2 of email-based customer registration.
 * Verifies the OTP sent to the email address and completes account creation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerEmailRegisterVerifyOtpRequest {

    @Schema(description = "Email address used during registration send-otp",
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
