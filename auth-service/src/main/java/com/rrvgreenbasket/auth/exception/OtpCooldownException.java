package com.rrvgreenbasket.auth.exception;

/**
 * OtpCooldownException
 *
 * <p><strong>What it does:</strong></p>
 * Thrown when an OTP resend is requested before the mandatory cooldown window has elapsed.
 */
public class OtpCooldownException extends RuntimeException {

    private final long remainingSeconds;

    public OtpCooldownException(String message, long remainingSeconds) {
        super(message);
        this.remainingSeconds = remainingSeconds;
    }

    public long getRemainingSeconds() {
        return remainingSeconds;
    }
}
