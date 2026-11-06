package com.rrvgreenbasket.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CustomerRegisterSendOtpRequest
 *
 * <p><strong>What it does:</strong></p>
 * Inbound payload initiating customer registration and requesting a 6-digit OTP challenge.
 * Staged registration metadata (full name, email) is linked to this challenge.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerRegisterSendOtpRequest {

    @Schema(description = "Customer full name", example = "Mahendra Kumar", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Full name is required for registration")
    @Size(min = 2, max = 150, message = "Full name must be between 2 and 150 characters")
    private String fullName;

    @Schema(description = "Customer mobile number (10 digits or E.164)", example = "+919876500001", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Mobile number is required")
    private String mobileNumber;

    @Schema(description = "Optional email address", example = "mahendra@example.com")
    @Email(message = "Invalid email format")
    @Size(max = 254, message = "Email cannot exceed 254 characters")
    private String email;
}
