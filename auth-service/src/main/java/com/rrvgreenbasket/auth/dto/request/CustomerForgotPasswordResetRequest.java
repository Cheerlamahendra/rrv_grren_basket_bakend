package com.rrvgreenbasket.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

/**
 * CustomerForgotPasswordResetRequest
 *
 * <p><strong>What it does:</strong></p>
 * Inbound payload for Phase 3 of the forgot-password flow.
 * Consumes the short-lived reset authorization token and sets the new password.
 *
 * <p><strong>Security note:</strong></p>
 * confirmPassword validation is performed in the service layer. The reset token
 * is single-use and bound to a specific customer and PASSWORD_RESET purpose.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerForgotPasswordResetRequest {

    @Schema(description = "Short-lived reset authorization token issued after OTP verification",
            example = "eyJhbGciOiJub25lIn0...", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Reset token is required")
    private String resetToken;

    @Schema(description = "New password",
            example = "NewStrongPassword@123", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&_#^()\\-+=])[A-Za-z\\d@$!%*?&_#^()\\-+=]{8,}$",
        message = "Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character"
    )
    private String newPassword;

    @Schema(description = "Confirm new password – must match newPassword",
            example = "NewStrongPassword@123", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Password confirmation is required")
    private String confirmPassword;
}
