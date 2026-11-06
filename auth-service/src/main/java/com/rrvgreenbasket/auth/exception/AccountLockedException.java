package com.rrvgreenbasket.auth.exception;

/**
 * AccountLockedException
 *
 * <p><strong>What it does:</strong></p>
 * Thrown when an administrative user account is temporarily locked due to excessive failed password attempts.
 */
public class AccountLockedException extends RuntimeException {

    private final long lockoutRemainingMinutes;

    public AccountLockedException(String message, long lockoutRemainingMinutes) {
        super(message);
        this.lockoutRemainingMinutes = lockoutRemainingMinutes;
    }

    public long getLockoutRemainingMinutes() {
        return lockoutRemainingMinutes;
    }
}
