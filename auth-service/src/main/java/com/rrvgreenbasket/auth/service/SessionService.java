package com.rrvgreenbasket.auth.service;

import com.rrvgreenbasket.auth.dto.response.SessionValidationResponse;
import com.rrvgreenbasket.auth.entity.AuthUser;
import com.rrvgreenbasket.auth.entity.CustomerSession;

/**
 * SessionService Interface
 *
 * <p><strong>What it does:</strong></p>
 * Manages customer multi-device sessions and real-time revocation.
 */
public interface SessionService {

    CustomerSession createSession(AuthUser user, String deviceInfo, String ipAddress);

    SessionValidationResponse validateSession(String sessionId);

    boolean revokeSession(String sessionId, String reason);

    void touchSession(String sessionId);
}
