package com.rrvgreenbasket.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CustomerLoginSendOtpRequest
 *
 * <p><strong>What it does:</strong></p>
 * Inbound payload requesting a login OTP challenge for an existing registered customer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerLoginSendOtpRequest {

    @Schema(description = "Registered customer mobile number", example = "+919876500001", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Mobile number is required")
    private String mobileNumber;
}
