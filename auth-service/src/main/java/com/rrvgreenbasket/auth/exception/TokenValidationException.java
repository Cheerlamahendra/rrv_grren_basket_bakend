package com.rrvgreenbasket.auth.exception;

/**
 * TokenValidationException
 *
 * <p><strong>What it does:</strong></p>
 * Thrown when a JWT token or refresh token is malformed, expired, revoked, or untrusted.
 */
public class TokenValidationException extends RuntimeException {

    public TokenValidationException(String message) {
        super(message);
    }
}
