package com.rrvgreenbasket.auth.enums;

/**
 * SessionStatus
 *
 * <p><strong>What it does:</strong></p>
 * Database session status enum enforced by MySQL CHECK constraint:
 * <code>chk_customer_sessions_status CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED'))</code>
 */
public enum SessionStatus {
    ACTIVE,
    REVOKED,
    EXPIRED
}
