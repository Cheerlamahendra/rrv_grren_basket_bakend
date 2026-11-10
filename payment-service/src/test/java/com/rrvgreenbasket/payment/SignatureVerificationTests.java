package com.rrvgreenbasket.payment;

import com.rrvgreenbasket.payment.config.RazorpayProperties;
import com.rrvgreenbasket.payment.security.S2SSignatureUtil;
import com.rrvgreenbasket.payment.service.impl.RazorpayServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Unit Tests — Cryptographic Signature Verification")
class SignatureVerificationTests {

    private RazorpayProperties properties;
    private RazorpayServiceImpl razorpayService;
    private S2SSignatureUtil s2sSignatureUtil;

    private final String testKeySecret = "test_rzp_secret_key_12345";
    private final String testWebhookSecret = "test_rzp_webhook_secret_67890";
    private final String internalSecret = "c8f7e2d1a5b49382716054321fedcba9876543210abcdef0123456789abcdef0";

    @BeforeEach
    void setUp() {
        properties = new RazorpayProperties();
        properties.setKeyId("rzp_test_123");
        properties.setKeySecret(testKeySecret);
        properties.setWebhookSecret(testWebhookSecret);
        properties.setEnabled(true);

        razorpayService = new RazorpayServiceImpl(properties);
        razorpayService.init();

        s2sSignatureUtil = new S2SSignatureUtil();
    }

    @Test
    @DisplayName("Verify valid Razorpay checkout signature (orderId|paymentId)")
    void testValidCheckoutSignature() {
        String orderId = "order_EKwxwAgItmmMnv";
        String paymentId = "pay_G39gA56Qo4u41R";

        String payload = orderId + "|" + paymentId;
        String validSignature = calculateHmacSha256(payload, testKeySecret);

        boolean verified = razorpayService.verifyCheckoutSignature(orderId, paymentId, validSignature);
        assertTrue(verified, "Valid checkout signature must be verified successfully");
    }

    @Test
    @DisplayName("Reject tampered or invalid Razorpay checkout signature")
    void testInvalidCheckoutSignature() {
        String orderId = "order_EKwxwAgItmmMnv";
        String paymentId = "pay_G39gA56Qo4u41R";

        boolean verified = razorpayService.verifyCheckoutSignature(orderId, paymentId, "invalid_tampered_signature_hex");
        assertFalse(verified, "Invalid signature must be rejected");

        // Reject null or empty values
        assertFalse(razorpayService.verifyCheckoutSignature(null, paymentId, "sig"));
        assertFalse(razorpayService.verifyCheckoutSignature(orderId, null, "sig"));
        assertFalse(razorpayService.verifyCheckoutSignature(orderId, paymentId, null));
    }

    @Test
    @DisplayName("Verify valid Razorpay webhook raw payload signature")
    void testValidWebhookSignature() {
        String rawPayload = "{\"entity\":\"event\",\"account_id\":\"acc_123\",\"event\":\"payment.captured\"}";
        String validSignature = calculateHmacSha256(rawPayload, testWebhookSecret);

        boolean verified = razorpayService.verifyWebhookSignature(rawPayload, validSignature);
        assertTrue(verified, "Valid webhook signature must be verified successfully");
    }

    @Test
    @DisplayName("Reject invalid Razorpay webhook signature")
    void testInvalidWebhookSignature() {
        String rawPayload = "{\"entity\":\"event\",\"account_id\":\"acc_123\",\"event\":\"payment.captured\"}";
        boolean verified = razorpayService.verifyWebhookSignature(rawPayload, "bad_signature_value");
        assertFalse(verified, "Tampered webhook signature must be rejected");

        assertFalse(razorpayService.verifyWebhookSignature(null, "sig"));
        assertFalse(razorpayService.verifyWebhookSignature(rawPayload, null));
    }

    @Test
    @DisplayName("Verify S2S internal HMAC-SHA256 canonical request signature")
    void testS2SSignatureVerification() {
        String method = "POST";
        String path = "/api/internal/payments/pay-123/cod/collect";
        long timestamp = System.currentTimeMillis();
        String nonce = "nonce-abc-123";
        byte[] bodyBytes = "{\"collectedAmount\": 200.00}".getBytes(StandardCharsets.UTF_8);

        String bodyHash = s2sSignatureUtil.calculateBodyHash(bodyBytes);
        String canonicalString = s2sSignatureUtil.buildCanonicalString(method, path, timestamp, nonce, bodyHash);
        String signature = s2sSignatureUtil.calculateSignature(canonicalString, internalSecret);

        assertTrue(s2sSignatureUtil.verifySignature(canonicalString, internalSecret, signature));
        assertFalse(s2sSignatureUtil.verifySignature(canonicalString, internalSecret, "wrong_sig"));
    }

    private String calculateHmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
