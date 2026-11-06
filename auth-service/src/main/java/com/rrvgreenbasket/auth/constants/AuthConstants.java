package com.rrvgreenbasket.auth.constants;

/**
 * AuthConstants
 *
 * <p><strong>What it does:</strong></p>
 * Centralized constant definitions across the Authentication microservice.
 *
 * <p><strong>Why it is required:</strong></p>
 * Eliminates magic strings and keeps HTTP header names, token prefixes,
 * and correlation keys consistent.
 */
public final class AuthConstants {

    private AuthConstants() {
        // Prevent instantiation
    }

    public static final String TOKEN_TYPE_BEARER = "Bearer";
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    // JWT Claims
    public static final String CLAIM_PUBLIC_ID = "public_id";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_USER_TYPE = "user_type";

    // Downstream Injected Headers (Used by API Gateway)
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_EMAIL = "X-User-Email";
    public static final String HEADER_USER_ROLE = "X-User-Role";
}
