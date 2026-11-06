package com.rrvgreenbasket.auth.exception;

/**
 * OtpExpiredException
 *
 * <p><strong>What it does:</strong></p>
 * Thrown when an OTP is submitted after its configured validity window has lapsed.
 */
public class OtpExpiredException extends RuntimeException {

    public OtpExpiredException(String message) {
        super(message);
    }
}
