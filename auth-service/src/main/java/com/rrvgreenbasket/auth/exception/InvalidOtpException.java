package com.rrvgreenbasket.auth.exception;

/**
 * InvalidOtpException
 *
 * <p><strong>What it does:</strong></p>
 * Thrown when an OTP verification attempt fails due to mismatched code or exhausted attempts.
 */
public class InvalidOtpException extends RuntimeException {

    public InvalidOtpException(String message) {
        super(message);
    }
}
