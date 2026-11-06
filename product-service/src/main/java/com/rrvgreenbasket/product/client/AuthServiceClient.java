package com.rrvgreenbasket.product.client;

/**
 * AuthServiceClient
 *
 * <p><strong>Inter-Service Client Interface:</strong></p>
 * Authoritative session revocation verification contract with Auth Service.
 */
public interface AuthServiceClient {

    boolean isSessionActive(String sessionId);
}
