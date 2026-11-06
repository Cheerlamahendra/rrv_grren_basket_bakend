package com.rrvgreenbasket.auth.service;

import com.rrvgreenbasket.auth.dto.response.AuthResponse;
import com.rrvgreenbasket.auth.entity.AuthUser;

/**
 * JwtTokenService Interface
 *
 * <p><strong>What it does:</strong></p>
 * Manages token signing, claims extraction, family-based refresh token rotation,
 * session binding, and real-time revocation.
 */
public interface JwtTokenService {

    String generateAccessToken(AuthUser user);

    String generateAccessToken(AuthUser user, String sessionId);

    String createRefreshToken(AuthUser user);

    String createRefreshToken(AuthUser user, String sessionId);

    AuthResponse rotateRefreshToken(String rawRefreshToken);

    void revokeTokens(String rawAccessToken, String rawRefreshToken);

    void revokeSession(String sessionId, String reason);

    boolean validateAccessToken(String token);

    String extractIdentifier(String token);

    String extractPublicId(String token);

    String extractSessionId(String token);

    String hashToken(String rawToken);
}
