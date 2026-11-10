package com.rrvgreenbasket.payment.service.impl;

import com.razorpay.Order;
import com.razorpay.Payment;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.rrvgreenbasket.payment.config.RazorpayProperties;
import com.rrvgreenbasket.payment.exception.RazorpayIntegrationException;
import com.rrvgreenbasket.payment.service.RazorpayService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class RazorpayServiceImpl implements RazorpayService {

    private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";

    private final RazorpayProperties properties;
    private RazorpayClient razorpayClient;
    private boolean isMockMode = false;

    public RazorpayServiceImpl(RazorpayProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        if (!properties.isEnabled() || properties.getKeyId().contains("placeholder") || properties.getKeySecret().contains("placeholder")) {
            log.info("Razorpay credentials are placeholder/disabled. Running in SIMULATED test mode.");
            this.isMockMode = true;
            return;
        }

        try {
            this.razorpayClient = new RazorpayClient(properties.getKeyId(), properties.getKeySecret());
            log.info("Razorpay Client successfully initialized with key ID: {}", properties.getKeyId());
        } catch (RazorpayException e) {
            log.warn("Failed to initialize live RazorpayClient (running in test mode): {}", e.getMessage());
            this.isMockMode = true;
        }
    }

    @Override
    public RazorpayOrderResult createOrder(BigDecimal amountInRupees, String receipt, Map<String, String> notes) {
        if (amountInRupees == null || amountInRupees.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Order amount must be greater than zero");
        }

        long amountInPaise = amountInRupees.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();

        log.info("Creating Razorpay order: receipt={}, amountPaise={}, currency={}",
                receipt, amountInPaise, properties.getCurrency());

        if (isMockMode || razorpayClient == null) {
            String mockOrderId = "order_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
            log.debug("Simulated Razorpay order created: {}", mockOrderId);
            return new RazorpayOrderResult(mockOrderId, amountInPaise, properties.getCurrency(), properties.getKeyId());
        }

        try {
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountInPaise);
            orderRequest.put("currency", properties.getCurrency());
            orderRequest.put("receipt", receipt);

            if (notes != null && !notes.isEmpty()) {
                JSONObject notesObj = new JSONObject();
                notes.forEach(notesObj::put);
                orderRequest.put("notes", notesObj);
            }

            Order order = razorpayClient.orders.create(orderRequest);
            String razorpayOrderId = order.get("id");
            log.info("Live Razorpay order created successfully: {}", razorpayOrderId);

            return new RazorpayOrderResult(razorpayOrderId, amountInPaise, properties.getCurrency(), properties.getKeyId());

        } catch (RazorpayException e) {
            log.error("Failed to create Razorpay order for receipt '{}': {}", receipt, e.getMessage());
            throw new RazorpayIntegrationException("Failed to create order on Razorpay: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean verifyCheckoutSignature(String orderId, String paymentId, String signature) {
        if (orderId == null || paymentId == null || signature == null) {
            return false;
        }

        String data = orderId + "|" + paymentId;
        String calculatedSignature = calculateHmacSha256(data, properties.getKeySecret());

        return constantTimeEquals(calculatedSignature, signature);
    }

    @Override
    public boolean verifyWebhookSignature(String rawPayload, String signature) {
        if (rawPayload == null || signature == null) {
            return false;
        }

        String calculatedSignature = calculateHmacSha256(rawPayload, properties.getWebhookSecret());
        return constantTimeEquals(calculatedSignature, signature);
    }

    @Override
    public RazorpayPaymentDetails fetchPayment(String paymentId) {
        log.info("Fetching Razorpay payment details for paymentId: {}", paymentId);

        if (isMockMode || razorpayClient == null) {
            log.debug("Simulating payment details for paymentId: {}", paymentId);
            return new RazorpayPaymentDetails(paymentId, "simulated_order", "captured", 20000L, properties.getCurrency(), true);
        }

        try {
            Payment payment = razorpayClient.payments.fetch(paymentId);
            String status = payment.get("status");
            Integer amountInt = payment.get("amount");
            Long amount = amountInt != null ? amountInt.longValue() : 0L;
            String currency = payment.get("currency");
            String orderId = payment.get("order_id");
            boolean captured = "captured".equalsIgnoreCase(status);

            log.info("Fetched live payment status: paymentId={}, status={}, amount={}, captured={}",
                    paymentId, status, amount, captured);

            return new RazorpayPaymentDetails(paymentId, orderId, status, amount, currency, captured);

        } catch (RazorpayException e) {
            log.error("Failed to fetch Razorpay payment '{}': {}", paymentId, e.getMessage());
            throw new RazorpayIntegrationException("Failed to fetch payment status from Razorpay: " + e.getMessage(), e);
        }
    }

    @Override
    public RazorpayRefundResult createRefund(String paymentId, BigDecimal amountInRupees, String reason) {
        long amountInPaise = amountInRupees.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();

        log.info("Initiating refund with Razorpay: paymentId={}, amountPaise={}, reason={}",
                paymentId, amountInPaise, reason);

        if (isMockMode || razorpayClient == null) {
            String mockRefundId = "rfnd_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
            log.debug("Simulated Razorpay refund created: {}", mockRefundId);
            return new RazorpayRefundResult(mockRefundId, paymentId, amountInPaise, "processed");
        }

        try {
            JSONObject refundRequest = new JSONObject();
            refundRequest.put("amount", amountInPaise);
            refundRequest.put("reverse_all", 0);
            if (reason != null && !reason.isBlank()) {
                JSONObject notes = new JSONObject();
                notes.put("reason", reason);
                refundRequest.put("notes", notes);
            }

            com.razorpay.Refund refund = razorpayClient.payments.refund(paymentId, refundRequest);
            String refundId = refund.get("id");
            String status = refund.get("status");
            log.info("Live Razorpay refund processed successfully: id={}, status={}", refundId, status);

            return new RazorpayRefundResult(refundId, paymentId, amountInPaise, status);

        } catch (RazorpayException e) {
            log.error("Failed to process Razorpay refund for payment '{}': {}", paymentId, e.getMessage());
            throw new RazorpayIntegrationException("Failed to process refund on Razorpay: " + e.getMessage(), e);
        }
    }

    private String calculateHmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256_ALGORITHM);
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(rawHmac);
        } catch (Exception e) {
            log.error("HMAC SHA256 computation error: {}", e.getMessage());
            throw new IllegalStateException("Failed to calculate HMAC-SHA256 signature", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
