package com.rrvgreenbasket.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.payment.dto.request.AdminRefundRequest;
import com.rrvgreenbasket.payment.dto.response.PaymentRefundResponse;
import com.rrvgreenbasket.payment.entity.Payment;
import com.rrvgreenbasket.payment.entity.PaymentRefund;
import com.rrvgreenbasket.payment.entity.PaymentStatusHistory;
import com.rrvgreenbasket.payment.entity.PaymentTransaction;
import com.rrvgreenbasket.payment.enums.PaymentStatus;
import com.rrvgreenbasket.payment.enums.RefundStatus;
import com.rrvgreenbasket.payment.enums.TransactionStatus;
import com.rrvgreenbasket.payment.enums.TransactionType;
import com.rrvgreenbasket.payment.exception.PaymentNotFoundException;
import com.rrvgreenbasket.payment.exception.RefundException;
import com.rrvgreenbasket.payment.outbox.PaymentEventPublisher;
import com.rrvgreenbasket.payment.repository.PaymentRefundRepository;
import com.rrvgreenbasket.payment.repository.PaymentRepository;
import com.rrvgreenbasket.payment.repository.PaymentStatusHistoryRepository;
import com.rrvgreenbasket.payment.repository.PaymentTransactionRepository;
import com.rrvgreenbasket.payment.service.IdempotencyService;
import com.rrvgreenbasket.payment.service.RazorpayService;
import com.rrvgreenbasket.payment.service.RefundService;
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
public class RefundServiceImpl implements RefundService {

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final PaymentRefundRepository refundRepository;
    private final PaymentStatusHistoryRepository historyRepository;
    private final RazorpayService razorpayService;
    private final IdempotencyService idempotencyService;
    private final PaymentEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public PaymentRefundResponse processRefund(String paymentPublicId, AdminRefundRequest request, String adminReference) {
        log.info("Processing refund for payment '{}': amount=₹{}, reason='{}', idempotencyKey='{}'",
                paymentPublicId, request.getRefundAmount(), request.getRefundReason(), request.getIdempotencyKey());

        // 1. Idempotency Check
        String payloadJson = serializeRequest(request);
        IdempotencyService.IdempotencyCheck idempCheck = idempotencyService.checkAndAcquire(
                request.getIdempotencyKey(), "REFUND", paymentPublicId, payloadJson);

        if (idempCheck.result() == IdempotencyService.IdempotencyResult.ALREADY_COMPLETED) {
            log.info("Refund already processed for idempotency key '{}'. Returning existing record.", request.getIdempotencyKey());
            return returnExistingRefund(idempCheck.responseReference(), request.getIdempotencyKey());
        }

        try {
            // 2. Fetch and Validate Payment
            Payment payment = paymentRepository.findByPaymentPublicId(paymentPublicId)
                    .orElseThrow(() -> new PaymentNotFoundException("Payment record not found for public ID: " + paymentPublicId));

            // Must have a captured online payment
            if (payment.getAdvancePaidAmount() == null || payment.getAdvancePaidAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new RefundException("No online captured payment available to refund. Online advance paid is ₹0.00");
            }

            BigDecimal availableForRefund = payment.getAdvancePaidAmount().subtract(payment.getRefundedAmount());
            BigDecimal refundAmount = request.getRefundAmount();

            if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RefundException("Refund amount must be greater than zero.");
            }

            if (refundAmount.compareTo(availableForRefund) > 0) {
                throw new RefundException(String.format(
                        "Refund amount ₹%s exceeds the maximum refundable online amount ₹%s (already refunded: ₹%s)",
                        refundAmount, availableForRefund, payment.getRefundedAmount()));
            }

            // Find the captured online transaction to obtain providerPaymentId
            Optional<PaymentTransaction> capturedTxOpt = payment.getTransactions().stream()
                    .filter(tx -> (tx.getTransactionType() == TransactionType.ONLINE_PAYMENT
                            || tx.getTransactionType() == TransactionType.ONLINE_ADVANCE)
                            && tx.getTransactionStatus() == TransactionStatus.SUCCESS
                            && tx.getProviderPaymentId() != null)
                    .findFirst();

            if (capturedTxOpt.isEmpty()) {
                throw new RefundException("No successful captured Razorpay transaction found for payment: " + paymentPublicId);
            }

            PaymentTransaction capturedTx = capturedTxOpt.get();

            // 3. Initiate refund with Razorpay
            RazorpayService.RazorpayRefundResult rzpRefund = razorpayService.createRefund(
                    capturedTx.getProviderPaymentId(),
                    refundAmount,
                    request.getRefundReason()
            );

            // 4. Update Payment
            String previousStatus = payment.getPaymentStatus().name();
            BigDecimal newRefundedAmount = payment.getRefundedAmount().add(refundAmount);
            payment.setRefundedAmount(newRefundedAmount);

            boolean fullyRefunded = newRefundedAmount.compareTo(payment.getAdvancePaidAmount()) >= 0;
            if (fullyRefunded) {
                payment.setPaymentStatus(PaymentStatus.REFUNDED);
            } else {
                payment.setPaymentStatus(PaymentStatus.PARTIALLY_REFUNDED);
            }

            // 5. Record PaymentRefund
            String refundPublicId = UUID.randomUUID().toString();
            Instant now = Instant.now();

            PaymentRefund refund = PaymentRefund.builder()
                    .refundPublicId(refundPublicId)
                    .payment(payment)
                    .providerRefundId(rzpRefund.refundId())
                    .refundAmount(refundAmount)
                    .refundStatus(RefundStatus.PROCESSED)
                    .refundReason(request.getRefundReason())
                    .idempotencyKey(request.getIdempotencyKey())
                    .createdAt(now)
                    .build();
            refundRepository.save(refund);

            // 6. Record Refund Transaction
            PaymentTransaction refundTx = PaymentTransaction.builder()
                    .transactionPublicId(UUID.randomUUID().toString())
                    .payment(payment)
                    .transactionType(TransactionType.ONLINE_REFUND)
                    .transactionStatus(TransactionStatus.SUCCESS)
                    .amount(refundAmount)
                    .currency(payment.getCurrency())
                    .provider(payment.getTransactions().getFirst().getProvider())
                    .providerOrderId(capturedTx.getProviderOrderId())
                    .providerPaymentId(capturedTx.getProviderPaymentId())
                    .providerReference(rzpRefund.refundId())
                    .build();
            payment.addTransaction(refundTx);

            Payment updatedPayment = paymentRepository.save(payment);

            // 7. Status History
            String reason = String.format("Online refund of ₹%s processed via Razorpay (refundId: %s)",
                    refundAmount, rzpRefund.refundId());
            recordStatusHistory(updatedPayment, previousStatus, updatedPayment.getPaymentStatus().name(), reason, adminReference);

            // 8. Complete Idempotency
            idempotencyService.complete(request.getIdempotencyKey(), refundPublicId);

            // 9. Publish Outbox Events
            eventPublisher.publishEvent(updatedPayment, "REFUND_COMPLETED", capturedTx.getProviderPaymentId(), capturedTx.getProviderOrderId(), null);

            log.info("Refund completed successfully: refundPublicId={}, providerRefundId={}, refundedAmount=₹{}, newStatus={}",
                    refundPublicId, rzpRefund.refundId(), refundAmount, updatedPayment.getPaymentStatus());

            return PaymentRefundResponse.builder()
                    .refundPublicId(refundPublicId)
                    .paymentPublicId(payment.getPaymentPublicId())
                    .providerRefundId(rzpRefund.refundId())
                    .refundAmount(refundAmount)
                    .refundStatus(RefundStatus.PROCESSED)
                    .refundReason(request.getRefundReason())
                    .idempotencyKey(request.getIdempotencyKey())
                    .createdAt(now)
                    .build();

        } catch (Exception ex) {
            idempotencyService.fail(request.getIdempotencyKey());
            log.error("Failed to process refund for payment '{}': {}", paymentPublicId, ex.getMessage());
            throw ex;
        }
    }

    private PaymentRefundResponse returnExistingRefund(String refundPublicId, String idempotencyKey) {
        Optional<PaymentRefund> refundOpt = (refundPublicId != null)
                ? refundRepository.findByRefundPublicId(refundPublicId)
                : refundRepository.findByIdempotencyKey(idempotencyKey);

        if (refundOpt.isPresent()) {
            PaymentRefund r = refundOpt.get();
            return PaymentRefundResponse.builder()
                    .refundPublicId(r.getRefundPublicId())
                    .paymentPublicId(r.getPayment().getPaymentPublicId())
                    .providerRefundId(r.getProviderRefundId())
                    .refundAmount(r.getRefundAmount())
                    .refundStatus(r.getRefundStatus())
                    .refundReason(r.getRefundReason())
                    .idempotencyKey(r.getIdempotencyKey())
                    .createdAt(r.getCreatedAt())
                    .build();
        }

        throw new RefundException("Idempotent refund record could not be found for key: " + idempotencyKey);
    }

    private void recordStatusHistory(Payment payment, String prev, String current, String reason, String changedBy) {
        PaymentStatusHistory history = PaymentStatusHistory.builder()
                .payment(payment)
                .previousStatus(prev)
                .newStatus(current)
                .reason(reason)
                .changedBy(changedBy != null ? changedBy : "ADMIN")
                .changedAt(Instant.now())
                .build();
        historyRepository.save(history);
    }

    private String serializeRequest(AdminRefundRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            return request.toString();
        }
    }
}
