package com.rrvgreenbasket.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CustomerLogoutRequest
 *
 * <p><strong>What it does:</strong></p>
 * Inbound payload for customer logout, supplying the device's refresh token.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerLogoutRequest {

    @Schema(description = "Refresh token for the device session to be revoked", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Refresh token is required for logout")
    private String refreshToken;
}
