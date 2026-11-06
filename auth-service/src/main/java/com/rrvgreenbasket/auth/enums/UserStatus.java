package com.rrvgreenbasket.auth.enums;

/**
 * UserStatus
 *
 * <p><strong>What it does:</strong></p>
 * Database user status enum enforced by MySQL CHECK constraint:
 * <code>chk_auth_users_status CHECK (status IN ('PENDING', 'ACTIVE', 'BLOCKED', 'DISABLED'))</code>
 */
public enum UserStatus {
    PENDING,
    ACTIVE,
    BLOCKED,
    DISABLED
}
