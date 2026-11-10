package com.rrvgreenbasket.payment.service.impl;

import com.rrvgreenbasket.payment.client.OrderServiceClient;
import com.rrvgreenbasket.payment.client.dto.OrderDetailsDto;
import com.rrvgreenbasket.payment.config.PartialCodProperties;
import com.rrvgreenbasket.payment.dto.request.PartialCodPaymentInitiateRequest;
import com.rrvgreenbasket.payment.dto.response.PartialCodPaymentInitiateResponse;
import com.rrvgreenbasket.payment.entity.Payment;
import com.rrvgreenbasket.payment.entity.PaymentStatusHistory;
import com.rrvgreenbasket.payment.entity.PaymentTransaction;
import com.rrvgreenbasket.payment.enums.PaymentMethod;
import com.rrvgreenbasket.payment.enums.PaymentProvider;
import com.rrvgreenbasket.payment.enums.PaymentStatus;
import com.rrvgreenbasket.payment.enums.TransactionStatus;
import com.rrvgreenbasket.payment.enums.TransactionType;
import com.rrvgreenbasket.payment.exception.DuplicatePaymentException;
import com.rrvgreenbasket.payment.exception.InvalidPaymentStateException;
import com.rrvgreenbasket.payment.exception.InvalidRequestException;
import com.rrvgreenbasket.payment.exception.UnauthorizedPaymentAccessException;
import com.rrvgreenbasket.payment.outbox.PaymentEventPublisher;
import com.rrvgreenbasket.payment.repository.PaymentRepository;
import com.rrvgreenbasket.payment.repository.PaymentStatusHistoryRepository;
import com.rrvgreenbasket.payment.repository.PaymentTransactionRepository;
import com.rrvgreenbasket.payment.service.PartialPaymentService;
import com.rrvgreenbasket.payment.service.RazorpayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PartialPaymentServiceImpl implements PartialPaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final PaymentStatusHistoryRepository historyRepository;
    private final OrderServiceClient orderServiceClient;
    private final RazorpayService razorpayService;
    private final PartialCodProperties partialCodProperties;
    private final PaymentEventPublisher eventPublisher;

    @Override
    @Transactional
    public PartialCodPaymentInitiateResponse initiatePartialCod(PartialCodPaymentInitiateRequest request, String customerPublicId) {
        log.info("Initiating 50% partial online + COD payment for order '{}' by customer '{}'",
                request.getOrderPublicId(), customerPublicId);

        if (!partialCodProperties.isEnabled()) {
            throw new InvalidPaymentStateException("Partial COD payment method is currently disabled.");
        }

        // 1. Fetch trusted order details
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

        // 3. Precise amount calculation according to Section 4.2
        int advancePercentage = partialCodProperties.getAdvancePercentage();
        BigDecimal advanceAmount = totalAmount
                .multiply(BigDecimal.valueOf(advancePercentage))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal deliveryAmount = totalAmount.subtract(advanceAmount);

        log.info("Calculated partial COD breakdown for order '{}': Total ₹{}, Advance ({}%) ₹{}, Cash at Delivery ₹{}",
                order.getOrderPublicId(), totalAmount, advancePercentage, advanceAmount, deliveryAmount);

        String paymentPublicId = UUID.randomUUID().toString();
        String receipt = "RRV_ADV_" + order.getOrderPublicId();

        Map<String, String> notes = new HashMap<>();
        notes.put("paymentPublicId", paymentPublicId);
        notes.put("orderPublicId", order.getOrderPublicId());
        notes.put("customerPublicId", customerPublicId);
        notes.put("paymentMethod", PaymentMethod.PARTIAL_ONLINE_COD.name());
        notes.put("advancePercentage", String.valueOf(advancePercentage));

        // 4. Create Razorpay order for ONLY the 50% advance amount!
        RazorpayService.RazorpayOrderResult razorpayOrder = razorpayService.createOrder(advanceAmount, receipt, notes);

        // 5. Create Payment entity
        Payment payment = Payment.builder()
                .paymentPublicId(paymentPublicId)
                .orderPublicId(order.getOrderPublicId())
                .customerPublicId(customerPublicId)
                .paymentMethod(PaymentMethod.PARTIAL_ONLINE_COD)
                .paymentStatus(PaymentStatus.ADVANCE_PAYMENT_INITIATED)
                .currency(razorpayOrder.currency())
                .totalAmount(totalAmount)
                .advancePercentage(advancePercentage)
                .advanceAmount(advanceAmount)
                .advancePaidAmount(BigDecimal.ZERO)
                .deliveryCollectionAmount(deliveryAmount)
                .deliveryCollectedAmount(BigDecimal.ZERO)
                .outstandingAmount(totalAmount)
                .refundedAmount(BigDecimal.ZERO)
                .build();

        // 6. Create Transaction entity
        PaymentTransaction transaction = PaymentTransaction.builder()
                .transactionPublicId(UUID.randomUUID().toString())
                .payment(payment)
                .transactionType(TransactionType.ONLINE_ADVANCE)
                .transactionStatus(TransactionStatus.INITIATED)
                .amount(advanceAmount)
                .currency(razorpayOrder.currency())
                .provider(PaymentProvider.RAZORPAY)
                .providerOrderId(razorpayOrder.orderId())
                .providerReference(receipt)
                .build();

        payment.addTransaction(transaction);
        Payment savedPayment = paymentRepository.save(payment);

        // 7. Audit history
        recordStatusHistory(savedPayment, null, PaymentStatus.ADVANCE_PAYMENT_INITIATED.name(),
                "50% Advance online payment initiated via Razorpay", customerPublicId);

        // 8. Publish outbox event
        eventPublisher.publishEvent(savedPayment, "PAYMENT_INITIATED", null, razorpayOrder.orderId(), null);

        log.info("Partial COD payment initiated successfully: paymentPublicId={}, razorpayOrderId={}, advancePaise={}",
                paymentPublicId, razorpayOrder.orderId(), razorpayOrder.amountInPaise());

        return PartialCodPaymentInitiateResponse.builder()
                .paymentPublicId(paymentPublicId)
                .orderPublicId(order.getOrderPublicId())
                .paymentMethod(PaymentMethod.PARTIAL_ONLINE_COD)
                .totalAmount(totalAmount)
                .advancePercentage(advancePercentage)
                .advanceAmount(advanceAmount)
                .deliveryCollectionAmount(deliveryAmount)
                .advancePaidAmount(BigDecimal.ZERO)
                .deliveryCollectedAmount(BigDecimal.ZERO)
                .outstandingAmount(totalAmount)
                .currency(razorpayOrder.currency())
                .paymentStatus(PaymentStatus.ADVANCE_PAYMENT_INITIATED)
                .razorpayOrderId(razorpayOrder.orderId())
                .razorpayKeyId(razorpayOrder.keyId())
                .razorpayAmount(razorpayOrder.amountInPaise())
                .build();
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
}
