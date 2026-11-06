package com.rrvgreenbasket.auth.service;

import com.rrvgreenbasket.auth.entity.AuthUser;

/**
 * AuthAuditService Interface
 *
 * <p><strong>What it does:</strong></p>
 * Central service contract for recording authentication audit events in <code>auth_audit_events</code>.
 */
public interface AuthAuditService {

    void logEvent(AuthUser user, String eventType, boolean success, String ipAddress, String details);
}
