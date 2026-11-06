package com.rrvgreenbasket.auth.dto.request;

import com.rrvgreenbasket.auth.enums.OtpPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SendOtpRequest
 *
 * <p><strong>What it does:</strong></p>
 * Inbound payload requesting a 6-digit verification OTP.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendOtpRequest {

    @Schema(description = "Customer 10-digit mobile number or E.164 format", example = "+918247329044")
    private String mobileNumber;

    @Schema(description = "Customer email address (optional alternative)", example = "customer@example.com")
    private String email;

    @Schema(description = "Customer full name (optional for registration)", example = "Mahendra Kumar")
    private String fullName;

    @Schema(description = "Workflow context for the OTP", example = "LOGIN", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "OTP purpose is required (LOGIN, REGISTRATION, or MOBILE_CHANGE)")
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
