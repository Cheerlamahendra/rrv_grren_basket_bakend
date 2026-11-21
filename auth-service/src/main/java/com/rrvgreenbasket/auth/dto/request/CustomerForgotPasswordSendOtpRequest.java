package com.rrvgreenbasket.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

/**
 * CustomerForgotPasswordSendOtpRequest
 *
 * <p><strong>What it does:</strong></p>
 * Inbound payload for Phase 1 of the forgot-password flow.
 * Requests a PASSWORD_RESET OTP to be sent to the provided email address.
 *
 * <p><strong>Security note:</strong></p>
 * The API will return a consistent success response regardless of whether the
 * email address is registered, to prevent email enumeration attacks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerForgotPasswordSendOtpRequest {

    @Schema(description = "Email address associated with the customer account",
            example = "mahendra@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Email address is required")
    @Email(message = "Email must be a valid email address")
    @Size(max = 254, message = "Email cannot exceed 254 characters")
    private String email;
}
