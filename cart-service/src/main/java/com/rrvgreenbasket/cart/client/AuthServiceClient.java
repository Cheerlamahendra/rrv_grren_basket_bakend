package com.rrvgreenbasket.cart.client;

/**
 * AuthServiceClient
 *
 * <p><strong>What it does:</strong></p>
 * Service-to-Service client for validating active customer sessions with Auth Service.
 */
public interface AuthServiceClient {

    /**
     * Checks if the given session ID is active and not revoked in Auth Service.
     *
     * @param sessionId Session ID claim from gateway assertion
     * @return true if session is valid and active, false if revoked, expired, or invalid
     */
    boolean isSessionActive(String sessionId);
}
