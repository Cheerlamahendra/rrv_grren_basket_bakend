package com.rrvgreenbasket.auth.dto.response;

import com.rrvgreenbasket.auth.constants.AuthConstants;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AuthResponse
 *
 * <p><strong>What it does:</strong></p>
 * Standard authentication response returned on successful customer/admin login or token refresh.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    @Builder.Default
    private String tokenType = AuthConstants.TOKEN_TYPE_BEARER;
    private long expiresInSeconds;
    private UserSummaryResponse user;
}
