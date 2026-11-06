package com.rrvgreenbasket.auth.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * EmailDeliveryResult
 *
 * <p><strong>What it does:</strong></p>
 * Audit model capturing the outcome of an email dispatch operation.
 *
 * <p><strong>Security Note:</strong></p>
 * Contains delivery metadata and status only; never stores sensitive OTP values or credentials.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailDeliveryResult {

    private boolean delivered;
    private String providerName;
    private String messageId;
    private String statusMessage;
    @Builder.Default
    private Instant timestamp = Instant.now();

    public static EmailDeliveryResult mockDispatched(String recipientEmail) {
        return EmailDeliveryResult.builder()
                .delivered(false) // Prompt constraint: Do not report successful email delivery when using an unconfigured mock provider
                .providerName("MOCK_EMAIL_PROVIDER")
                .messageId("MOCK-" + System.currentTimeMillis())
                .statusMessage("Mock email processed locally. External Email API provider not yet configured.")
                .timestamp(Instant.now())
                .build();
    }
}
