package com.rrvgreenbasket.auth.client.impl;

import com.rrvgreenbasket.auth.client.EmailProvider;
import com.rrvgreenbasket.auth.client.dto.EmailDeliveryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * ResendEmailProviderAdapter
 *
 * <p><strong>What it does:</strong></p>
 * Production {@link EmailProvider} implementation that dispatches transactional OTP emails
 * via the Resend API (<a href="https://resend.com/docs/api-reference/emails/send-email">Resend Docs</a>).
 *
 * <p><strong>Activation:</strong></p>
 * Active when <code>app.email.provider=resend</code> is set in configuration.
 *
 * <p><strong>Security:</strong></p>
 * <ul>
 *   <li>API key is read from environment variable <code>RESEND_API_KEY</code>; never hardcoded.</li>
 *   <li>OTP values are NEVER logged.</li>
 *   <li>API key is NEVER logged or included in API responses.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "resend")
public class ResendEmailProviderAdapter implements EmailProvider {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailProviderAdapter.class);

    private final RestClient resendClient;
    private final String fromEmail;
    private final String apiKey;

    public ResendEmailProviderAdapter(
            @Value("${app.email.api-url:https://api.resend.com/emails}") String apiUrl,
            @Value("${app.email.api-key}") String apiKey,
            @Value("${app.email.from-email}") String fromEmail) {
        this.apiKey = apiKey;
        this.fromEmail = fromEmail;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);

        this.resendClient = RestClient.builder()
                .baseUrl(apiUrl)
                .requestFactory(factory)
                .build();
    }

    @Override
    public EmailDeliveryResult sendOtpEmail(String recipientEmail, String otpCode, int expirationMinutes) {
        String maskedEmail = maskEmail(recipientEmail);
        log.info("[RESEND] Dispatching OTP email to: {} (expires in {} min)", maskedEmail, expirationMinutes);

        Map<String, Object> payload = Map.of(
                "from", fromEmail,
                "to", List.of(recipientEmail),
                "subject", "RRV Green Basket – Email Verification OTP",
                "html", buildOtpEmailHtml(otpCode, expirationMinutes)
        );

        try {
            resendClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("[RESEND] OTP email accepted for delivery to: {}", maskedEmail);
            return EmailDeliveryResult.builder()
                    .delivered(true)
                    .providerName("RESEND")
                    .messageId("resend-" + System.currentTimeMillis())
                    .statusMessage("OTP email dispatched via Resend successfully")
                    .build();

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.FORBIDDEN) {
                log.error("[RESEND] Authentication failed – verify RESEND_API_KEY configuration. Status: {}", e.getStatusCode());
            } else {
                log.error("[RESEND] Email rejected by Resend API. Status: {}, Recipient: {}", e.getStatusCode(), maskedEmail);
            }
            return EmailDeliveryResult.builder()
                    .delivered(false)
                    .providerName("RESEND")
                    .statusMessage("Resend API rejected the request: " + e.getStatusCode())
                    .build();

        } catch (HttpServerErrorException e) {
            log.error("[RESEND] Resend API server error for recipient: {}. Status: {}", maskedEmail, e.getStatusCode());
            return EmailDeliveryResult.builder()
                    .delivered(false)
                    .providerName("RESEND")
                    .statusMessage("Resend API server error: " + e.getStatusCode())
                    .build();

        } catch (ResourceAccessException e) {
            log.error("[RESEND] Network connectivity failure while reaching Resend API for recipient: {}", maskedEmail);
            return EmailDeliveryResult.builder()
                    .delivered(false)
                    .providerName("RESEND")
                    .statusMessage("Network error: Unable to reach Resend API")
                    .build();

        } catch (Exception e) {
            log.error("[RESEND] Unexpected error dispatching email to: {}", maskedEmail, e);
            return EmailDeliveryResult.builder()
                    .delivered(false)
                    .providerName("RESEND")
                    .statusMessage("Unexpected email dispatch error")
                    .build();
        }
    }

    /**
     * Constructs the HTML email body for OTP verification.
     * The OTP code is embedded in the body but never logged.
     */
    private String buildOtpEmailHtml(String otpCode, int expirationMinutes) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                  <title>RRV Green Basket – Email Verification</title>
                </head>
                <body style="margin:0;padding:0;background-color:#f4f7f4;font-family:Arial,Helvetica,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f7f4;padding:40px 0;">
                    <tr>
                      <td align="center">
                        <table width="560" cellpadding="0" cellspacing="0"
                               style="background:#ffffff;border-radius:12px;overflow:hidden;
                                      box-shadow:0 4px 20px rgba(0,0,0,0.08);">
                          <!-- Header -->
                          <tr>
                            <td style="background:linear-gradient(135deg,#2e7d32,#43a047);
                                       padding:32px 40px;text-align:center;">
                              <h1 style="color:#ffffff;margin:0;font-size:24px;font-weight:700;
                                         letter-spacing:-0.5px;">🌿 RRV Green Basket</h1>
                              <p style="color:#c8e6c9;margin:8px 0 0;font-size:14px;">
                                Fresh &amp; Organic Grocery Delivery
                              </p>
                            </td>
                          </tr>
                          <!-- Body -->
                          <tr>
                            <td style="padding:40px 40px 32px;">
                              <h2 style="color:#1b5e20;font-size:20px;margin:0 0 16px;">
                                Email Verification Code
                              </h2>
                              <p style="color:#424242;font-size:15px;line-height:1.6;margin:0 0 24px;">
                                Use the verification code below to complete your registration.
                                Do not share this code with anyone.
                              </p>
                              <!-- OTP Box -->
                              <table width="100%%" cellpadding="0" cellspacing="0"
                                     style="margin:0 0 24px;">
                                <tr>
                                  <td align="center">
                                    <div style="background:#f1f8e9;border:2px dashed #81c784;
                                                border-radius:10px;padding:24px 32px;
                                                display:inline-block;">
                                      <span style="font-size:40px;font-weight:800;
                                                   color:#2e7d32;letter-spacing:12px;
                                                   font-family:monospace;">
                                        %s
                                      </span>
                                    </div>
                                  </td>
                                </tr>
                              </table>
                              <!-- Expiry notice -->
                              <table width="100%%" cellpadding="0" cellspacing="0"
                                     style="background:#fff8e1;border-radius:8px;
                                            border-left:4px solid #ffc107;margin:0 0 24px;">
                                <tr>
                                  <td style="padding:14px 18px;">
                                    <p style="margin:0;color:#5d4037;font-size:14px;">
                                      ⏳ &nbsp;This code expires in <strong>%d minute(s)</strong>.
                                      Do not share it with anyone.
                                    </p>
                                  </td>
                                </tr>
                              </table>
                              <p style="color:#757575;font-size:13px;line-height:1.6;margin:0;">
                                If you did not request this verification, please ignore this email.
                                Your account security is not at risk.
                              </p>
                            </td>
                          </tr>
                          <!-- Footer -->
                          <tr>
                            <td style="background:#f9fbe7;padding:20px 40px;
                                       border-top:1px solid #e8f5e9;text-align:center;">
                              <p style="margin:0;color:#9e9e9e;font-size:12px;">
                                © 2024 RRV Green Basket. All rights reserved.<br/>
                                This is an automated message – please do not reply.
                              </p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(otpCode, expirationMinutes);
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
