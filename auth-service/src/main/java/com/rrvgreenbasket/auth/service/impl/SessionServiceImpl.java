package com.rrvgreenbasket.auth.service.impl;

import com.rrvgreenbasket.auth.dto.response.SessionValidationResponse;
import com.rrvgreenbasket.auth.entity.AuthUser;
import com.rrvgreenbasket.auth.entity.CustomerSession;
import com.rrvgreenbasket.auth.enums.SessionStatus;
import com.rrvgreenbasket.auth.enums.UserStatus;
import com.rrvgreenbasket.auth.repository.CustomerSessionRepository;
import com.rrvgreenbasket.auth.repository.RefreshTokenRepository;
import com.rrvgreenbasket.auth.service.AuthAuditService;
import com.rrvgreenbasket.auth.service.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * SessionServiceImpl
 *
 * <p><strong>What it does:</strong></p>
 * Implements session creation, validation, touch, and revocation.
 * Guarantees zero invalidation lag by treating MySQL <code>customer_sessions</code>
 * as the authoritative state.
 */
@Service
public class SessionServiceImpl implements SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionServiceImpl.class);

    private final CustomerSessionRepository customerSessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthAuditService auditService;

    public SessionServiceImpl(
            CustomerSessionRepository customerSessionRepository,
            RefreshTokenRepository refreshTokenRepository,
            AuthAuditService auditService) {
        this.customerSessionRepository = customerSessionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public CustomerSession createSession(AuthUser user, String deviceInfo, String ipAddress) {
        String sessionId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        CustomerSession session = CustomerSession.builder()
                .sessionId(sessionId)
                .authUser(user)
                .deviceInfo(deviceInfo)
                .ipAddress(ipAddress)
                .status(SessionStatus.ACTIVE)
                .createdAt(now)
                .lastActiveAt(now)
                .build();

        CustomerSession saved = customerSessionRepository.save(session);
        log.info("Created customer session id='{}' for user publicId='{}'", sessionId, user.getPublicId());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public SessionValidationResponse validateSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return SessionValidationResponse.builder().valid(false).build();
        }

        Optional<CustomerSession> sessionOpt = customerSessionRepository.findBySessionId(sessionId);
        if (sessionOpt.isEmpty()) {
            return SessionValidationResponse.builder().valid(false).sessionId(sessionId).build();
        }

        CustomerSession session = sessionOpt.get();
        AuthUser user = session.getAuthUser();

        boolean isActive = session.getStatus() == SessionStatus.ACTIVE
                && user.getStatus() == UserStatus.ACTIVE;

        String role = "ROLE_" + user.getRole().name();
        if ("ROLE_USER".equals(role)) {
            role = "ROLE_CUSTOMER";
        }

        return SessionValidationResponse.builder()
                .valid(isActive)
                .sessionId(sessionId)
                .userId(user.getPublicId())
                .role(role)
                .status(session.getStatus().name())
                .build();
    }

    @Override
    @Transactional
    public boolean revokeSession(String sessionId, String reason) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }

        Optional<CustomerSession> sessionOpt = customerSessionRepository.findBySessionId(sessionId);
        if (sessionOpt.isEmpty()) {
            log.warn("Attempted to revoke non-existent session: {}", sessionId);
            return false;
        }

        CustomerSession session = sessionOpt.get();
        AuthUser authUser = session.getAuthUser();
        String userPublicId = authUser != null ? authUser.getPublicId() : "unknown";
        LocalDateTime now = LocalDateTime.now();

        session.setStatus(SessionStatus.REVOKED);
        session.setRevokedAt(now);
        session.setRevocationReason(reason != null ? reason : "LOGOUT");
        customerSessionRepository.save(session);

        // Also revoke the entire refresh token family linked to this session
        refreshTokenRepository.revokeFamily(sessionId, now);

        auditService.logEvent(authUser, "SESSION_REVOKED", true, session.getIpAddress(),
                "Session " + sessionId + " revoked. Reason: " + session.getRevocationReason());

        log.info("Successfully revoked session id='{}' for user='{}'", sessionId, userPublicId);
        return true;
    }

    @Override
    @Transactional
    public void touchSession(String sessionId) {
        customerSessionRepository.touchSession(sessionId, LocalDateTime.now());
    }
}
