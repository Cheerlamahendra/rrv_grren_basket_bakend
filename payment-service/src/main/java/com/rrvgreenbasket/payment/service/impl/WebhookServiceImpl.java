package com.rrvgreenbasket.payment.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.payment.entity.Payment;
import com.rrvgreenbasket.payment.entity.PaymentStatusHistory;
import com.rrvgreenbasket.payment.entity.PaymentTransaction;
import com.rrvgreenbasket.payment.entity.PaymentWebhookEvent;
import com.rrvgreenbasket.payment.enums.PaymentMethod;
import com.rrvgreenbasket.payment.enums.PaymentProvider;
import com.rrvgreenbasket.payment.enums.PaymentStatus;
import com.rrvgreenbasket.payment.enums.TransactionStatus;
import com.rrvgreenbasket.payment.enums.TransactionType;
import com.rrvgreenbasket.payment.enums.WebhookProcessingStatus;
import com.rrvgreenbasket.payment.exception.InvalidSignatureException;
import com.rrvgreenbasket.payment.outbox.PaymentEventPublisher;
import com.rrvgreenbasket.payment.repository.PaymentRepository;
import com.rrvgreenbasket.payment.repository.PaymentStatusHistoryRepository;
import com.rrvgreenbasket.payment.repository.PaymentTransactionRepository;
import com.rrvgreenbasket.payment.repository.PaymentWebhookEventRepository;
import com.rrvgreenbasket.payment.service.RazorpayService;
import com.rrvgreenbasket.payment.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookServiceImpl implements WebhookService {

    private final PaymentWebhookEventRepository webhookEventRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final PaymentStatusHistoryRepository historyRepository;
    private final RazorpayService razorpayService;
    private final PaymentEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void processRazorpayWebhook(String rawPayload, String signature) {
        log.info("Processing inbound Razorpay webhook");

        // 1. Verify HMAC-SHA256 signature using the raw body
        boolean signatureValid = razorpayService.verifyWebhookSignature(rawPayload, signature);
        if (!signatureValid) {
            log.error("Razorpay webhook signature verification failed!");
            throw new InvalidSignatureException("Invalid Razorpay webhook cryptographic signature.");
        }

        // 2. Parse payload
        JsonNode rootNode;
        String eventId;
        String eventType;
        try {
            rootNode = objectMapper.readTree(rawPayload);
            eventId = rootNode.path("event_id").asText(null);
            if (eventId == null || eventId.isBlank()) {
                eventId = rootNode.path("id").asText(UUID.randomUUID().toString());
            }
            eventType = rootNode.path("event").asText("");
        } catch (Exception ex) {
            log.error("Failed to parse Razorpay webhook JSON payload: {}", ex.getMessage());
            throw new IllegalArgumentException("Malformed webhook JSON payload: " + ex.getMessage(), ex);
        }

        log.info("Webhook event received: id='{}', event='{}'", eventId, eventType);

        // 3. Deduplication check via Inbox
        if (webhookEventRepository.existsByProviderAndProviderEventId(PaymentProvider.RAZORPAY, eventId)) {
            log.info("Duplicate webhook event ignored: id='{}', event='{}'", eventId, eventType);
            return;
        }

        // 4. Record event in Inbox as RECEIVED
        PaymentWebhookEvent webhookEntry = PaymentWebhookEvent.builder()
                .provider(PaymentProvider.RAZORPAY)
                .providerEventId(eventId)
                .eventType(eventType)
                .eventPayload(rawPayload)
                .processingStatus(WebhookProcessingStatus.PROCESSING)
                .receivedAt(Instant.now())
                .build();
        webhookEntry = webhookEventRepository.save(webhookEntry);

        try {
            // 5. Dispatch event
            dispatchWebhookEvent(eventType, rootNode);
            webhookEntry.setProcessingStatus(WebhookProcessingStatus.PROCESSED);
            webhookEntry.setProcessedAt(Instant.now());
            log.info("Webhook event '{}' successfully processed", eventId);

        } catch (Exception ex) {
            log.error("Error processing webhook event '{}': {}", eventId, ex.getMessage(), ex);
            webhookEntry.setProcessingStatus(WebhookProcessingStatus.FAILED);
            webhookEntry.setErrorMessage(ex.getMessage());
        }

        webhookEventRepository.save(webhookEntry);
    }

    private void dispatchWebhookEvent(String eventType, JsonNode rootNode) {
        JsonNode entityNode = rootNode.path("payload").path("payment").path("entity");
        if (entityNode.isMissingNode() || entityNode.isNull()) {
            entityNode = rootNode.path("payload").path("refund").path("entity");
        }

        switch (eventType) {
            case "payment.captured" -> handlePaymentCaptured(entityNode);
            case "payment.authorized" -> handlePaymentAuthorized(entityNode);
            case "payment.failed" -> handlePaymentFailed(entityNode);
            case "refund.created", "refund.processed" -> handleRefundProcessed(entityNode);
            case "refund.failed" -> handleRefundFailed(entityNode);
            default -> log.info("Webhook event '{}' received but ignored (no state action required)", eventType);
        }
    }

    private void handlePaymentCaptured(JsonNode paymentNode) {
        if (paymentNode == null || paymentNode.isMissingNode()) {
            return;
        }

        String razorpayOrderId = paymentNode.path("order_id").asText(null);
        String razorpayPaymentId = paymentNode.path("id").asText(null);
        long amountPaise = paymentNode.path("amount").asLong(0);

        log.info("Handling payment.captured webhook: rzpOrderId='{}', rzpPaymentId='{}', amountPaise={}",
                razorpayOrderId, razorpayPaymentId, amountPaise);

        Optional<Payment> paymentOpt = findPaymentForRazorpayOrder(razorpayOrderId, paymentNode);
        if (paymentOpt.isEmpty()) {
            log.warn("No payment found matching Razorpay order ID '{}'", razorpayOrderId);
            return;
        }

        Payment payment = paymentOpt.get();

        // Idempotency check: if already fully paid or advance captured, ignore duplicate
        if (payment.getPaymentStatus() == PaymentStatus.PAID) {
            log.info("Payment '{}' is already in PAID state. Webhook ignored.", payment.getPaymentPublicId());
            return;
        }

        if (payment.isPartialCod() && payment.getPaymentStatus() == PaymentStatus.DELIVERY_COLLECTION_PENDING) {
            log.info("Partial COD payment '{}' is already in DELIVERY_COLLECTION_PENDING state. Webhook ignored.",
                    payment.getPaymentPublicId());
            return;
        }

        String previousStatus = payment.getPaymentStatus().name();

        if (payment.isPartialCod()) {
            // Advance payment captured
            payment.setAdvancePaidAmount(payment.getAdvanceAmount());
            payment.setOutstandingAmount(payment.getDeliveryCollectionAmount());
            payment.setPaymentStatus(PaymentStatus.DELIVERY_COLLECTION_PENDING);

            PaymentTransaction tx = PaymentTransaction.builder()
                    .transactionPublicId(UUID.randomUUID().toString())
                    .payment(payment)
                    .transactionType(TransactionType.ONLINE_ADVANCE)
                    .transactionStatus(TransactionStatus.SUCCESS)
                    .amount(payment.getAdvanceAmount())
                    .currency(payment.getCurrency())
                    .provider(PaymentProvider.RAZORPAY)
                    .providerOrderId(razorpayOrderId)
                    .providerPaymentId(razorpayPaymentId)
                    .build();
            payment.addTransaction(tx);

            recordStatusHistory(payment, previousStatus, PaymentStatus.DELIVERY_COLLECTION_PENDING.name(),
                    "50% Advance captured via Razorpay webhook", "WEBHOOK");

            Payment updated = paymentRepository.save(payment);
            eventPublisher.publishEvent(updated, "PARTIAL_PAYMENT_CAPTURED", razorpayPaymentId, razorpayOrderId, null);

        } else {
            // Full online payment captured
            payment.setAdvancePaidAmount(payment.getTotalAmount());
            payment.setOutstandingAmount(BigDecimal.ZERO);
            payment.setPaymentStatus(PaymentStatus.PAID);

            PaymentTransaction tx = PaymentTransaction.builder()
                    .transactionPublicId(UUID.randomUUID().toString())
                    .payment(payment)
                    .transactionType(TransactionType.ONLINE_PAYMENT)
                    .transactionStatus(TransactionStatus.SUCCESS)
                    .amount(payment.getTotalAmount())
                    .currency(payment.getCurrency())
                    .provider(PaymentProvider.RAZORPAY)
                    .providerOrderId(razorpayOrderId)
                    .providerPaymentId(razorpayPaymentId)
                    .build();
            payment.addTransaction(tx);

            recordStatusHistory(payment, previousStatus, PaymentStatus.PAID.name(),
                    "100% Full online payment captured via Razorpay webhook", "WEBHOOK");

            Payment updated = paymentRepository.save(payment);
            eventPublisher.publishEvent(updated, "PAYMENT_CAPTURED", razorpayPaymentId, razorpayOrderId, null);
            eventPublisher.publishEvent(updated, "PAYMENT_COMPLETED", razorpayPaymentId, razorpayOrderId, null);
        }
    }

    private void handlePaymentAuthorized(JsonNode paymentNode) {
        String razorpayOrderId = paymentNode.path("order_id").asText(null);
        Optional<Payment> paymentOpt = findPaymentForRazorpayOrder(razorpayOrderId, paymentNode);
        if (paymentOpt.isEmpty()) {
            return;
        }

        Payment payment = paymentOpt.get();
        // Never downgrade captured payment
        if (payment.getPaymentStatus() == PaymentStatus.PAID
                || payment.getPaymentStatus() == PaymentStatus.DELIVERY_COLLECTION_PENDING) {
            log.info("Ignoring payment.authorized webhook for already captured payment '{}'", payment.getPaymentPublicId());
            return;
        }

        payment.setPaymentStatus(PaymentStatus.AUTHORIZED);
        paymentRepository.save(payment);
        log.info("Payment '{}' updated to AUTHORIZED via webhook", payment.getPaymentPublicId());
    }

    private void handlePaymentFailed(JsonNode paymentNode) {
        String razorpayOrderId = paymentNode.path("order_id").asText(null);
        String razorpayPaymentId = paymentNode.path("id").asText(null);
        String errorCode = paymentNode.path("error_code").asText("PAYMENT_FAILED");
        String errorDescription = paymentNode.path("error_description").asText("Payment failed on gateway");

        Optional<Payment> paymentOpt = findPaymentForRazorpayOrder(razorpayOrderId, paymentNode);
        if (paymentOpt.isEmpty()) {
            return;
        }

        Payment payment = paymentOpt.get();
        // Never downgrade captured payment to failed (out-of-order webhook protection)
        if (payment.getPaymentStatus() == PaymentStatus.PAID
                || payment.getPaymentStatus() == PaymentStatus.DELIVERY_COLLECTION_PENDING) {
            log.warn("Late payment.failed webhook received for already captured payment '{}'. Ignoring downgrade.",
                    payment.getPaymentPublicId());
            return;
        }

        String prev = payment.getPaymentStatus().name();
        payment.setPaymentStatus(PaymentStatus.FAILED);

        PaymentTransaction tx = PaymentTransaction.builder()
                .transactionPublicId(UUID.randomUUID().toString())
                .payment(payment)
                .transactionType(payment.isPartialCod() ? TransactionType.ONLINE_ADVANCE : TransactionType.ONLINE_PAYMENT)
                .transactionStatus(TransactionStatus.FAILED)
                .amount(payment.isPartialCod() ? payment.getAdvanceAmount() : payment.getTotalAmount())
                .currency(payment.getCurrency())
                .provider(PaymentProvider.RAZORPAY)
                .providerOrderId(razorpayOrderId)
                .providerPaymentId(razorpayPaymentId)
                .failureCode(errorCode)
                .failureDescription(errorDescription)
                .build();
        payment.addTransaction(tx);

        recordStatusHistory(payment, prev, PaymentStatus.FAILED.name(), "Razorpay payment failure: " + errorDescription, "WEBHOOK");
        Payment updated = paymentRepository.save(payment);

        eventPublisher.publishEvent(updated, "PAYMENT_FAILED", razorpayPaymentId, razorpayOrderId, errorDescription);
        log.info("Payment '{}' marked as FAILED via webhook", updated.getPaymentPublicId());
    }

    private void handleRefundProcessed(JsonNode refundNode) {
        String refundId = refundNode.path("id").asText(null);
        String paymentId = refundNode.path("payment_id").asText(null);
        log.info("Handling refund webhook: refundId='{}', paymentId='{}'", refundId, paymentId);
    }

    private void handleRefundFailed(JsonNode refundNode) {
        String refundId = refundNode.path("id").asText(null);
        log.warn("Handling refund.failed webhook: refundId='{}'", refundId);
    }

    private Optional<Payment> findPaymentForRazorpayOrder(String razorpayOrderId, JsonNode node) {
        // First try finding transaction by providerOrderId
        if (razorpayOrderId != null && !razorpayOrderId.isBlank()) {
            Optional<PaymentTransaction> txOpt = transactionRepository.findByProviderOrderId(razorpayOrderId);
            if (txOpt.isPresent()) {
                return Optional.of(txOpt.get().getPayment());
            }
        }

        // Second try checking notes inside the JSON payload
        JsonNode notes = node.path("notes");
        if (!notes.isMissingNode() && !notes.isNull()) {
            String paymentPublicId = notes.path("paymentPublicId").asText(null);
            if (paymentPublicId != null && !paymentPublicId.isBlank()) {
                return paymentRepository.findByPaymentPublicId(paymentPublicId);
            }
            String orderPublicId = notes.path("orderPublicId").asText(null);
            if (orderPublicId != null && !orderPublicId.isBlank()) {
                return paymentRepository.findByOrderPublicId(orderPublicId);
            }
        }

        return Optional.empty();
    }

    private void recordStatusHistory(Payment payment, String prev, String current, String reason, String changedBy) {
        PaymentStatusHistory history = PaymentStatusHistory.builder()
                .payment(payment)
                .previousStatus(prev)
                .newStatus(current)
                .reason(reason)
                .changedBy(changedBy != null ? changedBy : "WEBHOOK")
                .changedAt(Instant.now())
                .build();
        historyRepository.save(history);
    }
}
