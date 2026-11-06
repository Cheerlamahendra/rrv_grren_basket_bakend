package com.rrvgreenbasket.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RefreshTokenRequest
 *
 * <p><strong>What it does:</strong></p>
 * Payload containing an existing refresh token string used to rotate and mint
 * a new JWT access/refresh token pair.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenRequest {

    @Schema(description = "Valid, non-expired refresh token string", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Refresh token must not be blank")
    private String refreshToken;
}
