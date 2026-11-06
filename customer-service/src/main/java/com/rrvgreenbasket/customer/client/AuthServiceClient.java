package com.rrvgreenbasket.customer.client;

/**
 * AuthServiceClient Interface
 *
 * <p><strong>What it does:</strong></p>
 * Inter-service client for Customer Service to validate live session status with Auth Service.
 */
public interface AuthServiceClient {

    boolean isSessionActive(String sessionId);
}
