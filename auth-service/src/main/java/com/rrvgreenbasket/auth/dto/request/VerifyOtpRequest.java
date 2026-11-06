package com.rrvgreenbasket.auth.dto.request;

import com.rrvgreenbasket.auth.enums.OtpPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * VerifyOtpRequest
 *
 * <p><strong>What it does:</strong></p>
 * Inbound payload for standalone OTP verification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyOtpRequest {

    @Schema(description = "Customer mobile number", example = "+918247329044")
    private String mobileNumber;

    @Schema(description = "Customer email address (optional alternative)", example = "customer@example.com")
    private String email;

    @Schema(description = "Customer full name (optional fallback)", example = "Mahendra Kumar")
    private String fullName;

    @Schema(description = "Six-digit numeric OTP code", example = "482910", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "OTP code is required")
    @Pattern(regexp = "^\\d{6}$", message = "OTP must be exactly 6 numeric digits")
    private String otp;

    @Schema(description = "Workflow context for the OTP (optional; auto-detected if omitted)", example = "REGISTRATION")
    private OtpPurpose purpose;

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
