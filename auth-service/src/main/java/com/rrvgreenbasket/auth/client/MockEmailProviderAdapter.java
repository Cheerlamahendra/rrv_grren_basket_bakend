package com.rrvgreenbasket.auth.client;

import com.rrvgreenbasket.auth.client.dto.EmailDeliveryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * MockEmailProviderAdapter
 *
 * <p><strong>What it does:</strong></p>
 * Mock implementation of {@link EmailProvider} for local development and offline testing.
 *
 * <p><strong>Mandatory Project Constraints:</strong></p>
 * <ul>
 *   <li>Active when <code>app.email.provider=mock</code> (or by default when no external provider is set).</li>
 *   <li>Does NOT report successful delivery (returns <code>delivered=false</code>) to accurately reflect that an unconfigured mock is active.</li>
 *   <li>NEVER logs OTP values, secrets, or API credentials.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "mock", matchIfMissing = true)
public class MockEmailProviderAdapter implements EmailProvider {

    private static final Logger log = LoggerFactory.getLogger(MockEmailProviderAdapter.class);

    @Override
    public EmailDeliveryResult sendOtpEmail(String recipientEmail, String otpCode, int expirationMinutes) {
        String maskedEmail = maskEmail(recipientEmail);
        log.info("[MOCK EMAIL PROVIDER] Simulating OTP dispatch to recipient: {} (expires in {} mins). " +
                "External Email API provider is not yet configured. OTP code is NOT logged for security.",
                maskedEmail, expirationMinutes);

        return EmailDeliveryResult.mockDispatched(recipientEmail);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIndex = email.indexOf('@');
        String username = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (username.length() <= 2) {
            return username.charAt(0) + "***" + domain;
        }
        return username.charAt(0) + "***" + username.charAt(username.length() - 1) + domain;
    }
}
