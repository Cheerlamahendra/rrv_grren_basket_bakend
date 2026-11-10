package com.rrvgreenbasket.payment.service;

import java.math.BigDecimal;
import java.util.Map;

public interface RazorpayService {

    record RazorpayOrderResult(String orderId, Long amountInPaise, String currency, String keyId) {}

    record RazorpayPaymentDetails(String paymentId, String orderId, String status, Long amountInPaise, String currency, boolean captured) {}

    record RazorpayRefundResult(String refundId, String paymentId, Long amountInPaise, String status) {}

    RazorpayOrderResult createOrder(BigDecimal amountInRupees, String receipt, Map<String, String> notes);

    boolean verifyCheckoutSignature(String orderId, String paymentId, String signature);

    boolean verifyWebhookSignature(String rawPayload, String signature);

    RazorpayPaymentDetails fetchPayment(String paymentId);

    RazorpayRefundResult createRefund(String paymentId, BigDecimal amountInRupees, String reason);
}
