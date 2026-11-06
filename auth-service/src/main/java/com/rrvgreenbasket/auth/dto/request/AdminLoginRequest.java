package com.rrvgreenbasket.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AdminLoginRequest
 *
 * <p><strong>What it does:</strong></p>
 * Inbound payload for platform administrative authentication via email and BCrypt password.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminLoginRequest {

    @Schema(description = "Administrator email address", example = "admin@rrvgreenbasket.com", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Admin email address is required")
    @Email(message = "Please provide a valid email address format")
    private String email;

    @Schema(description = "Administrator password", example = "Admin@123", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Password is required")
    private String password;
}
