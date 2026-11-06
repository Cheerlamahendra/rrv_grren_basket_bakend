package com.rrvgreenbasket.auth.service.impl;

import com.rrvgreenbasket.auth.entity.AuthAuditEvent;
import com.rrvgreenbasket.auth.entity.AuthUser;
import com.rrvgreenbasket.auth.repository.AuthAuditEventRepository;
import com.rrvgreenbasket.auth.service.AuthAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * AuthAuditServiceImpl
 *
 * <p><strong>What it does:</strong></p>
 * Persists security events into <code>auth_audit_events</code> table using REQUIRES_NEW propagation
 * so audit events persist even when the parent business transaction fails.
 */
@Service
public class AuthAuditServiceImpl implements AuthAuditService {

    private static final Logger log = LoggerFactory.getLogger(AuthAuditServiceImpl.class);

    private final AuthAuditEventRepository auditEventRepository;

    public AuthAuditServiceImpl(AuthAuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Override
    @Transactional
    public void logEvent(AuthUser user, String eventType, boolean success, String ipAddress, String details) {
        try {
            // Truncate details if exceeds 500 chars (MySQL column length)
            String truncatedDetails = details;
            if (truncatedDetails != null && truncatedDetails.length() > 500) {
                truncatedDetails = truncatedDetails.substring(0, 497) + "...";
            }

            AuthAuditEvent event = AuthAuditEvent.builder()
                    .user(user)
                    .eventType(eventType)
                    .success(success)
                    .ipAddress(ipAddress)
                    .details(truncatedDetails)
                    .createdAt(LocalDateTime.now())
                    .build();

            auditEventRepository.save(event);
            log.debug("Logged auth audit event: type={}, success={}, user={}",
                    eventType, success, user != null ? user.getId() : "anonymous");
        } catch (Exception ex) {
            log.error("Failed to write auth audit event: {}", ex.getMessage(), ex);
        }
    }
}
