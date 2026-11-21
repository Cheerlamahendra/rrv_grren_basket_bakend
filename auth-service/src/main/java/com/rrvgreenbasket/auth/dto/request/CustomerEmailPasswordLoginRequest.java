package com.rrvgreenbasket.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

/**
 * CustomerEmailPasswordLoginRequest
 *
 * <p><strong>What it does:</strong></p>
 * Inbound payload for email/password customer login.
 * Replaces the mobile OTP login flow as the primary login method.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerEmailPasswordLoginRequest {

    @Schema(description = "Customer email address",
            example = "mahendra@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Email address is required")
    @Email(message = "Email must be a valid email address")
    @Size(max = 254, message = "Email cannot exceed 254 characters")
    private String email;

    @Schema(description = "Customer password",
            example = "StrongPassword@123", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Password is required")
    @Size(min = 1, max = 128, message = "Password is required")
    private String password;
}
