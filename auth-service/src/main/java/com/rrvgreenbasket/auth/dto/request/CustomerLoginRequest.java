package com.rrvgreenbasket.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CustomerLoginRequest
 *
 * <p><strong>What it does:</strong></p>
 * Payload for customer OTP authentication via mobile number or email.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerLoginRequest {

    @Schema(description = "Registered customer mobile number", example = "+918247329044")
    private String mobileNumber;

    @Schema(description = "Customer email address (alternative)", example = "customer@example.com")
    private String email;

    @Schema(description = "Six-digit numeric OTP code", example = "482910", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "OTP code is required")
    @Pattern(regexp = "^\\d{6}$", message = "OTP must be exactly 6 numeric digits")
    private String otp;

    public String resolveDestination() {
        if (mobileNumber != null && !mobileNumber.isBlank()) {
            return mobileNumber.trim();
        }
        if (email != null && !email.isBlank()) {
            return email.trim();
        }
        throw new IllegalArgumentException("Either mobileNumber or email must be provided");
    }
}
