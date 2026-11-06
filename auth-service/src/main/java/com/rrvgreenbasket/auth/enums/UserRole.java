package com.rrvgreenbasket.auth.enums;

/**
 * UserRole
 *
 * <p><strong>What it does:</strong></p>
 * Database role enum enforced by MySQL CHECK constraint:
 * <code>chk_auth_users_role CHECK (role IN ('USER', 'ADMIN', 'CUSTOMER'))</code>
 */
public enum UserRole {
    CUSTOMER,
    ADMIN,
    USER // Retained for backward compatibility with legacy unmigrated records
}
