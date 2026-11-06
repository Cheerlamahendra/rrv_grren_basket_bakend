package com.rrvgreenbasket.auth.client;

import com.rrvgreenbasket.auth.client.dto.EmailDeliveryResult;

/**
 * EmailProvider Interface (Hexagonal Architecture Port)
 *
 * <p><strong>What it does:</strong></p>
 * Defines the contract for sending transactional emails (such as customer OTPs)
 * via an external Email API provider.
 *
 * <p><strong>Why it is required:</strong></p>
 * Decouples the authentication and OTP services from any proprietary email vendor API.
 * An external adapter will be implemented once the client provides the official Email API
 * specifications, endpoints, and credentials.
 */
public interface EmailProvider {

    /**
     * Dispatches a customer OTP verification email.
     *
     * @param recipientEmail   Recipient's email address
     * @param otpCode          The generated 6-digit OTP code (used only in the email body, never logged)
     * @param expirationMinutes Validity period in minutes
     * @return EmailDeliveryResult containing audit status
     */
    EmailDeliveryResult sendOtpEmail(String recipientEmail, String otpCode, int expirationMinutes);
}
