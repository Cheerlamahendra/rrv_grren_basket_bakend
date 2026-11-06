package com.rrvgreenbasket.auth.client;

/**
 * CustomerServiceClient Interface
 *
 * <p><strong>What it does:</strong></p>
 * Service-to-Service HTTP client communicating with Customer Service.
 * Manages idempotent profile initialization, verification probes, and compensation routines.
 */
public interface CustomerServiceClient {

    boolean initializeCustomerProfile(String authUserId, String fullName, String mobileNumber, String email);

    boolean verifyProfileExists(String authUserId);

    void compensateCustomerProfile(String authUserId);
}
