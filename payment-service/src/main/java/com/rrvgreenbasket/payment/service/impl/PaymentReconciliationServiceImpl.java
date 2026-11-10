package com.rrvgreenbasket.payment.service.impl;

import com.rrvgreenbasket.payment.entity.Payment;
import com.rrvgreenbasket.payment.entity.PaymentStatusHistory;
import com.rrvgreenbasket.payment.entity.PaymentTransaction;
import com.rrvgreenbasket.payment.enums.PaymentStatus;
import com.rrvgreenbasket.payment.enums.TransactionStatus;
import com.rrvgreenbasket.payment.enums.TransactionType;
import com.rrvgreenbasket.payment.outbox.PaymentEventPublisher;
import com.rrvgreenbasket.payment.repository.PaymentRepository;
import com.rrvgreenbasket.payment.repository.PaymentStatusHistoryRepository;
import com.rrvgreenbasket.payment.repository.PaymentTransactionRepository;
import com.rrvgreenbasket.payment.service.PaymentReconciliationService;
import com.rrvgreenbasket.payment.service.RazorpayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentReconciliationServiceImpl implements PaymentReconciliationService {

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final PaymentStatusHistoryRepository historyRepository;
    private final RazorpayService razorpayService;
    private final PaymentEventPublisher eventPublisher;

    @Value("${payment.reconciliation.pending-threshold-minutes:15}")
    private int pendingThresholdMinutes;

    @Override
    @Transactional
    public void reconcilePendingPayments() {
        Instant threshold = Instant.now().minus(pendingThresholdMinutes, ChronoUnit.MINUTES);
        List<PaymentStatus> pendingStatuses = List.of(PaymentStatus.INITIATED, PaymentStatus.ADVANCE_PAYMENT_INITIATED);

        List<Payment> pendingPayments = paymentRepository.findByPaymentStatusInAndCreatedAtBefore(pendingStatuses, threshold);
        if (pendingPayments.isEmpty()) {
            return;
        }

        log.info("Found {} pending payment(s) older than {} minutes to reconcile",
                pendingPayments.size(), pendingThresholdMinutes);

        for (Payment payment : pendingPayments) {
            try {
                reconcilePayment(payment.getPaymentPublicId());
            } catch (Exception ex) {
                log.warn("Reconciliation check failed for payment '{}': {}", payment.getPaymentPublicId(), ex.getMessage());
            }
        }
    }

    @Override
    @Transactional
    public void reconcilePayment(String paymentPublicId) {
        Payment payment = paymentRepository.findByPaymentPublicId(paymentPublicId).orElse(null);
        if (payment == null) {
            return;
        }

        if (payment.getPaymentStatus() == PaymentStatus.PAID
                || (payment.isPartialCod() && payment.getPaymentStatus() == PaymentStatus.DELIVERY_COLLECTION_PENDING)) {
            return; // Already resolved
        }

        log.info("Reconciling payment: publicId='{}', orderId='{}', status='{}'",
                paymentPublicId, payment.getOrderPublicId(), payment.getPaymentStatus());

        List<PaymentTransaction> transactions = transactionRepository.findByPaymentIdOrderByCreatedAtDesc(payment.getId());
        if (transactions.isEmpty()) {
            return;
        }

        PaymentTransaction latestTx = transactions.getFirst();
        String providerPaymentId = latestTx.getProviderPaymentId();

        if (providerPaymentId != null && !providerPaymentId.isBlank()) {
            RazorpayService.RazorpayPaymentDetails details = razorpayService.fetchPayment(providerPaymentId);

            if (details.captured()) {
                log.info("Reconciliation resolved: Payment '{}' was captured at provider", paymentPublicId);
                String prev = payment.getPaymentStatus().name();

                if (payment.isPartialCod()) {
                    payment.setAdvancePaidAmount(payment.getAdvanceAmount());
                    payment.setOutstandingAmount(payment.getDeliveryCollectionAmount());
                    payment.setPaymentStatus(PaymentStatus.DELIVERY_COLLECTION_PENDING);

                    latestTx.setTransactionStatus(TransactionStatus.SUCCESS);
                    transactionRepository.save(latestTx);

                    recordStatusHistory(payment, prev, PaymentStatus.DELIVERY_COLLECTION_PENDING.name(), "Reconciliation confirmed captured advance", "RECONCILIATION");
                    Payment updated = paymentRepository.save(payment);
                    eventPublisher.publishEvent(updated, "PARTIAL_PAYMENT_CAPTURED", providerPaymentId, latestTx.getProviderOrderId(), null);

                } else {
                    payment.setAdvancePaidAmount(payment.getTotalAmount());
                    payment.setOutstandingAmount(BigDecimal.ZERO);
                    payment.setPaymentStatus(PaymentStatus.PAID);

                    latestTx.setTransactionStatus(TransactionStatus.SUCCESS);
                    transactionRepository.save(latestTx);

                    recordStatusHistory(payment, prev, PaymentStatus.PAID.name(), "Reconciliation confirmed full payment captured", "RECONCILIATION");
                    Payment updated = paymentRepository.save(payment);
                    eventPublisher.publishEvent(updated, "PAYMENT_CAPTURED", providerPaymentId, latestTx.getProviderOrderId(), null);
                    eventPublisher.publishEvent(updated, "PAYMENT_COMPLETED", providerPaymentId, latestTx.getProviderOrderId(), null);
                }
            } else if ("failed".equalsIgnoreCase(details.status())) {
                log.info("Reconciliation confirmed payment failure for payment '{}'", paymentPublicId);
                payment.setPaymentStatus(PaymentStatus.FAILED);
                latestTx.setTransactionStatus(TransactionStatus.FAILED);
                transactionRepository.save(latestTx);
                paymentRepository.save(payment);
                eventPublisher.publishEvent(payment, "PAYMENT_FAILED", providerPaymentId, latestTx.getProviderOrderId(), "Provider reported failed status");
            }
        }
    }

    private void recordStatusHistory(Payment payment, String prev, String current, String reason, String changedBy) {
        PaymentStatusHistory history = PaymentStatusHistory.builder()
                .payment(payment)
                .previousStatus(prev)
                .newStatus(current)
                .reason(reason)
                .changedBy(changedBy != null ? changedBy : "RECONCILIATION")
                .changedAt(Instant.now())
                .build();
        historyRepository.save(history);
    }
}
