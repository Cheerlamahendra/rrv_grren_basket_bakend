package com.rrvgreenbasket.payment.service.impl;

import com.rrvgreenbasket.payment.client.OrderServiceClient;
import com.rrvgreenbasket.payment.client.dto.OrderDetailsDto;
import com.rrvgreenbasket.payment.dto.request.OnlinePaymentInitiateRequest;
import com.rrvgreenbasket.payment.dto.request.PaymentVerificationRequest;
import com.rrvgreenbasket.payment.dto.response.OnlinePaymentInitiateResponse;
import com.rrvgreenbasket.payment.dto.response.PaymentResponse;
import com.rrvgreenbasket.payment.dto.response.PaymentTransactionResponse;
import com.rrvgreenbasket.payment.entity.Payment;
import com.rrvgreenbasket.payment.entity.PaymentStatusHistory;
import com.rrvgreenbasket.payment.entity.PaymentTransaction;
import com.rrvgreenbasket.payment.enums.PaymentMethod;
import com.rrvgreenbasket.payment.enums.PaymentProvider;
import com.rrvgreenbasket.payment.enums.PaymentStatus;
import com.rrvgreenbasket.payment.enums.TransactionStatus;
import com.rrvgreenbasket.payment.enums.TransactionType;
import com.rrvgreenbasket.payment.exception.*;
import com.rrvgreenbasket.payment.outbox.PaymentEventPublisher;
import com.rrvgreenbasket.payment.repository.PaymentRepository;
import com.rrvgreenbasket.payment.repository.PaymentStatusHistoryRepository;
import com.rrvgreenbasket.payment.repository.PaymentTransactionRepository;
import com.rrvgreenbasket.payment.service.OnlinePaymentService;
import com.rrvgreenbasket.payment.service.RazorpayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnlinePaymentServiceImpl implements OnlinePaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final PaymentStatusHistoryRepository historyRepository;
    private final OrderServiceClient orderServiceClient;
    private final RazorpayService razorpayService;
    private final PaymentEventPublisher eventPublisher;

    @Override
    @Transactional
    public OnlinePaymentInitiateResponse initiatePayment(OnlinePaymentInitiateRequest request, String customerPublicId) {
        log.info("Initiating full online payment for order: '{}' by customer: '{}'", request.getOrderPublicId(), customerPublicId);

        // 1. Fetch trusted order details from Order Service
        OrderDetailsDto order = orderServiceClient.getOrderForPayment(request.getOrderPublicId());
        validateOrderCustomerOwnership(order, customerPublicId);

        if (!order.isPaymentEligible()) {
            throw new InvalidPaymentStateException("Order is not eligible for payment. Current status: " + order.getOrderStatus());
        }

        BigDecimal totalAmount = order.getTotalAmount();
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRequestException("Invalid order amount. Amount must be positive.");
        }

        // 2. Check for existing payment
        Optional<Payment> existingOpt = paymentRepository.findByOrderPublicId(order.getOrderPublicId());
        if (existingOpt.isPresent()) {
            Payment existing = existingOpt.get();
            if (existing.getPaymentStatus() == PaymentStatus.PAID) {
                throw new DuplicatePaymentException("Order has already been fully paid.");
            }
            if (existing.getPaymentStatus() == PaymentStatus.DELIVERY_COLLECTION_PENDING) {
                throw new DuplicatePaymentException("Advance has already been paid for this order.");
            }
        }

        String paymentPublicId = UUID.randomUUID().toString();
        String receipt = "RRV_PAY_" + order.getOrderPublicId();

        Map<String, String> notes = new HashMap<>();
        notes.put("paymentPublicId", paymentPublicId);
        notes.put("orderPublicId", order.getOrderPublicId());
        notes.put("customerPublicId", customerPublicId);
        notes.put("paymentMethod", PaymentMethod.ONLINE_FULL.name());

        // 3. Create Razorpay order for 100% of order total
        RazorpayService.RazorpayOrderResult razorpayOrder = razorpayService.createOrder(totalAmount, receipt, notes);

        // 4. Save Payment entity
        Payment payment = Payment.builder()
                .paymentPublicId(paymentPublicId)
                .orderPublicId(order.getOrderPublicId())
                .customerPublicId(customerPublicId)
                .paymentMethod(PaymentMethod.ONLINE_FULL)
                .paymentStatus(PaymentStatus.INITIATED)
                .currency(razorpayOrder.currency())
                .totalAmount(totalAmount)
                .advancePercentage(100)
                .advanceAmount(totalAmount)
                .advancePaidAmount(BigDecimal.ZERO)
                .deliveryCollectionAmount(BigDecimal.ZERO)
                .deliveryCollectedAmount(BigDecimal.ZERO)
                .outstandingAmount(totalAmount)
                .refundedAmount(BigDecimal.ZERO)
                .build();

        // 5. Save Transaction entity
        PaymentTransaction transaction = PaymentTransaction.builder()
                .transactionPublicId(UUID.randomUUID().toString())
                .payment(payment)
                .transactionType(TransactionType.ONLINE_PAYMENT)
                .transactionStatus(TransactionStatus.INITIATED)
                .amount(totalAmount)
                .currency(razorpayOrder.currency())
                .provider(PaymentProvider.RAZORPAY)
                .providerOrderId(razorpayOrder.orderId())
                .providerReference(receipt)
                .build();

        payment.addTransaction(transaction);
        Payment savedPayment = paymentRepository.save(payment);

        // 6. Record status history
        recordStatusHistory(savedPayment, null, PaymentStatus.INITIATED.name(), "Online payment initiated by customer", customerPublicId);

        // 7. Publish outbox event
        eventPublisher.publishEvent(savedPayment, "PAYMENT_INITIATED", null, razorpayOrder.orderId(), null);

        log.info("Full online payment initiated successfully: paymentPublicId={}, razorpayOrderId={}",
                paymentPublicId, razorpayOrder.orderId());

        return OnlinePaymentInitiateResponse.builder()
                .paymentPublicId(paymentPublicId)
                .razorpayOrderId(razorpayOrder.orderId())
                .razorpayKeyId(razorpayOrder.keyId())
                .amount(razorpayOrder.amountInPaise())
                .currency(razorpayOrder.currency())
                .paymentMethod(PaymentMethod.ONLINE_FULL)
                .build();
    }

    @Override
    @Transactional
    public PaymentResponse verifyPayment(PaymentVerificationRequest request, String customerPublicId) {
        log.info("Verifying online payment: paymentPublicId={}, razorpayPaymentId={}",
                request.getPaymentPublicId(), request.getRazorpayPaymentId());

        Payment payment = paymentRepository.findByPaymentPublicId(request.getPaymentPublicId())
                .orElseThrow(() -> new PaymentNotFoundException("Payment record not found for public ID: " + request.getPaymentPublicId()));

        validatePaymentOwnership(payment, customerPublicId);

        // Idempotency: if already paid or captured, return current details
        if (payment.getPaymentStatus() == PaymentStatus.PAID) {
            log.info("Payment '{}' is already in PAID status. Returning idempotent success.", payment.getPaymentPublicId());
            return mapToPaymentResponse(payment);
        }

        if (payment.getPaymentStatus() == PaymentStatus.DELIVERY_COLLECTION_PENDING && payment.isPartialCod()) {
            log.info("Advance payment '{}' is already captured. Returning idempotent success.", payment.getPaymentPublicId());
            return mapToPaymentResponse(payment);
        }

        // 1. Verify Razorpay checkout signature
        boolean signatureValid = razorpayService.verifyCheckoutSignature(
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getRazorpaySignature()
        );

        if (!signatureValid) {
            log.warn("Invalid signature verification for payment '{}'", payment.getPaymentPublicId());
            recordFailedTransaction(payment, request, "SIGNATURE_VERIFICATION_FAILED", "Checkout signature mismatch");
            recordStatusHistory(payment, payment.getPaymentStatus().name(), PaymentStatus.FAILED.name(), "Invalid checkout signature", customerPublicId);
            payment.setPaymentStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            eventPublisher.publishEvent(payment, "PAYMENT_FAILED", request.getRazorpayPaymentId(), request.getRazorpayOrderId(), "Invalid checkout signature");
            throw new InvalidSignatureException("Razorpay checkout signature verification failed.");
        }

        // 2. Fetch authoritative payment status from Razorpay
        RazorpayService.RazorpayPaymentDetails paymentDetails = razorpayService.fetchPayment(request.getRazorpayPaymentId());

        if (!paymentDetails.captured()) {
            log.warn("Payment '{}' (id: {}) is not yet captured by Razorpay. Current status: {}",
                    payment.getPaymentPublicId(), request.getRazorpayPaymentId(), paymentDetails.status());
            throw new InvalidPaymentStateException("Payment state is " + paymentDetails.status() + " and not captured by provider.");
        }

        // 3. Verify amount and currency
        BigDecimal expectedRupees = payment.isPartialCod() ? payment.getAdvanceAmount() : payment.getTotalAmount();
        long expectedPaise = expectedRupees.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();

        if (paymentDetails.amountInPaise() != expectedPaise) {
            log.error("Amount mismatch for payment '{}'! Expected {} paise, but Razorpay reports {} paise",
                    payment.getPaymentPublicId(), expectedPaise, paymentDetails.amountInPaise());
            throw new PaymentAmountMismatchException(String.format("Payment amount mismatch. Expected: %d paise, Provider: %d paise",
                    expectedPaise, paymentDetails.amountInPaise()));
        }

        String previousStatus = payment.getPaymentStatus().name();

        // 4. Update status according to payment method
        if (payment.isPartialCod()) {
            // Partial COD advance payment captured
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
                    .providerOrderId(request.getRazorpayOrderId())
                    .providerPaymentId(request.getRazorpayPaymentId())
                    .build();
            payment.addTransaction(tx);

            recordStatusHistory(payment, previousStatus, PaymentStatus.DELIVERY_COLLECTION_PENDING.name(),
                    "50% Advance online payment captured successfully", customerPublicId);

            Payment updated = paymentRepository.save(payment);
            eventPublisher.publishEvent(updated, "PARTIAL_PAYMENT_CAPTURED", request.getRazorpayPaymentId(), request.getRazorpayOrderId(), null);

            log.info("Partial COD advance payment verified and captured: paymentPublicId={}, advancePaid={}, outstanding={}",
                    updated.getPaymentPublicId(), updated.getAdvancePaidAmount(), updated.getOutstandingAmount());
            return mapToPaymentResponse(updated);

        } else {
            // Full online payment captured
            payment.setAdvancePaidAmount(payment.getTotalAmount());
            payment.setOutstandingAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            payment.setPaymentStatus(PaymentStatus.PAID);

            PaymentTransaction tx = PaymentTransaction.builder()
                    .transactionPublicId(UUID.randomUUID().toString())
                    .payment(payment)
                    .transactionType(TransactionType.ONLINE_PAYMENT)
                    .transactionStatus(TransactionStatus.SUCCESS)
                    .amount(payment.getTotalAmount())
                    .currency(payment.getCurrency())
                    .provider(PaymentProvider.RAZORPAY)
                    .providerOrderId(request.getRazorpayOrderId())
                    .providerPaymentId(request.getRazorpayPaymentId())
                    .build();
            payment.addTransaction(tx);

            recordStatusHistory(payment, previousStatus, PaymentStatus.PAID.name(),
                    "100% Full online payment captured successfully", customerPublicId);

            Payment updated = paymentRepository.save(payment);
            eventPublisher.publishEvent(updated, "PAYMENT_CAPTURED", request.getRazorpayPaymentId(), request.getRazorpayOrderId(), null);
            eventPublisher.publishEvent(updated, "PAYMENT_COMPLETED", request.getRazorpayPaymentId(), request.getRazorpayOrderId(), null);

            log.info("Full online payment verified and captured: paymentPublicId={}, status=PAID", updated.getPaymentPublicId());
            return mapToPaymentResponse(updated);
        }
    }

    private void recordFailedTransaction(Payment payment, PaymentVerificationRequest request, String code, String desc) {
        PaymentTransaction tx = PaymentTransaction.builder()
                .transactionPublicId(UUID.randomUUID().toString())
                .payment(payment)
                .transactionType(payment.isPartialCod() ? TransactionType.ONLINE_ADVANCE : TransactionType.ONLINE_PAYMENT)
                .transactionStatus(TransactionStatus.FAILED)
                .amount(payment.isPartialCod() ? payment.getAdvanceAmount() : payment.getTotalAmount())
                .currency(payment.getCurrency())
                .provider(PaymentProvider.RAZORPAY)
                .providerOrderId(request.getRazorpayOrderId())
                .providerPaymentId(request.getRazorpayPaymentId())
                .failureCode(code)
                .failureDescription(desc)
                .build();
        payment.addTransaction(tx);
    }

    private void recordStatusHistory(Payment payment, String prev, String current, String reason, String changedBy) {
        PaymentStatusHistory history = PaymentStatusHistory.builder()
                .payment(payment)
                .previousStatus(prev)
                .newStatus(current)
                .reason(reason)
                .changedBy(changedBy != null ? changedBy : "SYSTEM")
                .changedAt(Instant.now())
                .build();
        historyRepository.save(history);
    }

    private void validateOrderCustomerOwnership(OrderDetailsDto order, String customerPublicId) {
        if (customerPublicId != null && !customerPublicId.isBlank()
                && !"ROLE_ADMIN".equalsIgnoreCase(customerPublicId)
                && !"ROLE_INTERNAL_SERVICE".equalsIgnoreCase(customerPublicId)
                && order.getCustomerPublicId() != null
                && !order.getCustomerPublicId().equalsIgnoreCase(customerPublicId)) {
            log.warn("Customer ID mismatch: authenticated customer '{}' does not own order customer '{}'",
                    customerPublicId, order.getCustomerPublicId());
            throw new UnauthorizedPaymentAccessException("You are not authorized to initiate payment for this order.");
        }
    }

    private void validatePaymentOwnership(Payment payment, String customerPublicId) {
        if (customerPublicId != null && !customerPublicId.isBlank()
                && !"ROLE_ADMIN".equalsIgnoreCase(customerPublicId)
                && !"ROLE_INTERNAL_SERVICE".equalsIgnoreCase(customerPublicId)
                && !payment.getCustomerPublicId().equalsIgnoreCase(customerPublicId)) {
            log.warn("Customer ID mismatch: customer '{}' does not own payment '{}'",
                    customerPublicId, payment.getPaymentPublicId());
            throw new UnauthorizedPaymentAccessException("You are not authorized to access or modify this payment.");
        }
    }

    private PaymentResponse mapToPaymentResponse(Payment payment) {
        List<PaymentTransactionResponse> txResponses = payment.getTransactions().stream()
                .map(tx -> PaymentTransactionResponse.builder()
                        .transactionPublicId(tx.getTransactionPublicId())
                        .transactionType(tx.getTransactionType())
                        .transactionStatus(tx.getTransactionStatus())
                        .amount(tx.getAmount())
                        .currency(tx.getCurrency())
                        .provider(tx.getProvider())
                        .providerOrderId(tx.getProviderOrderId())
                        .providerPaymentId(tx.getProviderPaymentId())
                        .failureCode(tx.getFailureCode())
                        .failureDescription(tx.getFailureDescription())
                        .createdAt(tx.getCreatedAt())
                        .build())
                .toList();

        return PaymentResponse.builder()
                .paymentPublicId(payment.getPaymentPublicId())
                .orderPublicId(payment.getOrderPublicId())
                .customerPublicId(payment.getCustomerPublicId())
                .paymentMethod(payment.getPaymentMethod())
                .paymentStatus(payment.getPaymentStatus())
                .currency(payment.getCurrency())
                .totalAmount(payment.getTotalAmount())
                .advancePercentage(payment.getAdvancePercentage())
                .advanceAmount(payment.getAdvanceAmount())
                .advancePaidAmount(payment.getAdvancePaidAmount())
                .deliveryCollectionAmount(payment.getDeliveryCollectionAmount())
                .deliveryCollectedAmount(payment.getDeliveryCollectedAmount())
                .outstandingAmount(payment.getOutstandingAmount())
                .refundedAmount(payment.getRefundedAmount())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .transactions(txResponses)
                .build();
    }
}
