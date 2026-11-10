package com.rrvgreenbasket.payment.service.impl;

import com.rrvgreenbasket.payment.dto.request.PaymentFilterRequest;
import com.rrvgreenbasket.payment.dto.response.*;
import com.rrvgreenbasket.payment.entity.*;
import com.rrvgreenbasket.payment.enums.PaymentMethod;
import com.rrvgreenbasket.payment.enums.PaymentProvider;
import com.rrvgreenbasket.payment.enums.PaymentStatus;
import com.rrvgreenbasket.payment.enums.TransactionStatus;
import com.rrvgreenbasket.payment.enums.TransactionType;
import com.rrvgreenbasket.payment.exception.InvalidPaymentStateException;
import com.rrvgreenbasket.payment.exception.PaymentNotFoundException;
import com.rrvgreenbasket.payment.exception.UnauthorizedPaymentAccessException;
import com.rrvgreenbasket.payment.repository.*;
import com.rrvgreenbasket.payment.service.PaymentService;
import com.rrvgreenbasket.payment.service.RazorpayService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final PaymentRefundRepository refundRepository;
    private final PaymentStatusHistoryRepository historyRepository;
    private final CodCollectionRepository codCollectionRepository;
    private final RazorpayService razorpayService;

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentDetails(String paymentPublicId, String customerPublicId, boolean isAdmin) {
        Payment payment = paymentRepository.findByPaymentPublicId(paymentPublicId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with public ID: " + paymentPublicId));

        if (!isAdmin) {
            validateOwnership(payment, customerPublicId);
        }

        return mapToPaymentResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentStatusResponse getPaymentStatus(String paymentPublicId, String customerPublicId, boolean isAdmin) {
        Payment payment = paymentRepository.findByPaymentPublicId(paymentPublicId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with public ID: " + paymentPublicId));

        if (!isAdmin) {
            validateOwnership(payment, customerPublicId);
        }

        return PaymentStatusResponse.builder()
                .paymentPublicId(payment.getPaymentPublicId())
                .orderPublicId(payment.getOrderPublicId())
                .paymentStatus(payment.getPaymentStatus())
                .paymentMethod(payment.getPaymentMethod())
                .totalAmount(payment.getTotalAmount())
                .advancePaidAmount(payment.getAdvancePaidAmount())
                .deliveryCollectedAmount(payment.getDeliveryCollectedAmount())
                .outstandingAmount(payment.getOutstandingAmount())
                .currency(payment.getCurrency())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaymentHistoryResponse> getCustomerPaymentHistory(String customerPublicId, Pageable pageable) {
        Page<Payment> page = paymentRepository.findByCustomerPublicId(customerPublicId, pageable);
        return PageResponse.of(page.map(this::mapToHistoryResponse));
    }

    @Override
    @Transactional
    public OnlinePaymentInitiateResponse retryPayment(String paymentPublicId, String customerPublicId) {
        log.info("Retrying payment '{}' by customer '{}'", paymentPublicId, customerPublicId);

        Payment payment = paymentRepository.findByPaymentPublicId(paymentPublicId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with public ID: " + paymentPublicId));

        validateOwnership(payment, customerPublicId);

        if (payment.getPaymentStatus() == PaymentStatus.PAID) {
            throw new InvalidPaymentStateException("Cannot retry payment. Order is already fully paid.");
        }

        if (payment.isPartialCod() && payment.getPaymentStatus() == PaymentStatus.DELIVERY_COLLECTION_PENDING) {
            throw new InvalidPaymentStateException("Online advance has already been paid for this order.");
        }

        BigDecimal retryAmount = payment.isPartialCod() ? payment.getAdvanceAmount() : payment.getTotalAmount();
        String receipt = (payment.isPartialCod() ? "RRV_ADV_" : "RRV_PAY_") + payment.getOrderPublicId();

        Map<String, String> notes = new HashMap<>();
        notes.put("paymentPublicId", payment.getPaymentPublicId());
        notes.put("orderPublicId", payment.getOrderPublicId());
        notes.put("customerPublicId", customerPublicId);
        notes.put("paymentMethod", payment.getPaymentMethod().name());

        RazorpayService.RazorpayOrderResult rzpOrder = razorpayService.createOrder(retryAmount, receipt, notes);

        // Record retry transaction
        PaymentTransaction tx = PaymentTransaction.builder()
                .transactionPublicId(UUID.randomUUID().toString())
                .payment(payment)
                .transactionType(payment.isPartialCod() ? TransactionType.ONLINE_ADVANCE : TransactionType.ONLINE_PAYMENT)
                .transactionStatus(TransactionStatus.INITIATED)
                .amount(retryAmount)
                .currency(payment.getCurrency())
                .provider(PaymentProvider.RAZORPAY)
                .providerOrderId(rzpOrder.orderId())
                .providerReference(receipt)
                .build();
        payment.addTransaction(tx);

        payment.setPaymentStatus(payment.isPartialCod() ? PaymentStatus.ADVANCE_PAYMENT_INITIATED : PaymentStatus.INITIATED);
        paymentRepository.save(payment);

        recordStatusHistory(payment, "RETRY", payment.getPaymentStatus().name(), "Customer requested payment retry", customerPublicId);

        log.info("Payment retry initialized successfully: new rzpOrderId='{}'", rzpOrder.orderId());

        return OnlinePaymentInitiateResponse.builder()
                .paymentPublicId(payment.getPaymentPublicId())
                .razorpayOrderId(rzpOrder.orderId())
                .razorpayKeyId(rzpOrder.keyId())
                .amount(rzpOrder.amountInPaise())
                .currency(rzpOrder.currency())
                .paymentMethod(payment.getPaymentMethod())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentRefundResponse> getPaymentRefunds(String paymentPublicId, String customerPublicId, boolean isAdmin) {
        Payment payment = paymentRepository.findByPaymentPublicId(paymentPublicId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with public ID: " + paymentPublicId));

        if (!isAdmin) {
            validateOwnership(payment, customerPublicId);
        }

        List<PaymentRefund> refunds = refundRepository.findByPaymentIdOrderByCreatedAtDesc(payment.getId());
        return refunds.stream()
                .map(r -> PaymentRefundResponse.builder()
                        .refundPublicId(r.getRefundPublicId())
                        .paymentPublicId(payment.getPaymentPublicId())
                        .providerRefundId(r.getProviderRefundId())
                        .refundAmount(r.getRefundAmount())
                        .refundStatus(r.getRefundStatus())
                        .refundReason(r.getRefundReason())
                        .idempotencyKey(r.getIdempotencyKey())
                        .createdAt(r.getCreatedAt())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaymentHistoryResponse> getAdminPayments(PaymentFilterRequest filter, Pageable pageable) {
        Specification<Payment> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter != null) {
                if (filter.getStatus() != null) {
                    predicates.add(cb.equal(root.get("paymentStatus"), filter.getStatus()));
                }
                if (filter.getMethod() != null) {
                    predicates.add(cb.equal(root.get("paymentMethod"), filter.getMethod()));
                }
                if (filter.getOrderPublicId() != null && !filter.getOrderPublicId().isBlank()) {
                    predicates.add(cb.equal(root.get("orderPublicId"), filter.getOrderPublicId().trim()));
                }
                if (filter.getCustomerPublicId() != null && !filter.getCustomerPublicId().isBlank()) {
                    predicates.add(cb.equal(root.get("customerPublicId"), filter.getCustomerPublicId().trim()));
                }
                if (filter.getStartDate() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.getStartDate()));
                }
                if (filter.getEndDate() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.getEndDate()));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Payment> page = paymentRepository.findAll(spec, pageable);
        return PageResponse.of(page.map(this::mapToHistoryResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentStatisticsResponse getPaymentStatistics() {
        BigDecimal totalOrders = paymentRepository.sumTotalOrderAmount();
        long totalPayments = paymentRepository.countTotalPayments();
        long fullOnline = paymentRepository.countByPaymentMethod(PaymentMethod.ONLINE_FULL);
        long partialCod = paymentRepository.countByPaymentMethod(PaymentMethod.PARTIAL_ONLINE_COD);

        BigDecimal advancePayable = paymentRepository.sumTotalAdvanceAmountPayable();
        BigDecimal advancePaid = paymentRepository.sumTotalAdvancePaidAmount();
        BigDecimal deliveryPayable = paymentRepository.sumTotalDeliveryCashPayable();
        BigDecimal deliveryCollected = paymentRepository.sumTotalDeliveryCashCollected();
        BigDecimal outstanding = paymentRepository.sumTotalOutstandingAmount();
        BigDecimal refunded = paymentRepository.sumTotalRefundedAmount();
        BigDecimal totalCollected = advancePaid.add(deliveryCollected);

        long paidCount = paymentRepository.countByPaymentStatus(PaymentStatus.PAID);
        long failedCount = paymentRepository.countByPaymentStatus(PaymentStatus.FAILED);
        long pendingCount = paymentRepository.countByPaymentStatus(PaymentStatus.INITIATED)
                + paymentRepository.countByPaymentStatus(PaymentStatus.PENDING);
        long partiallyPaidCount = paymentRepository.countByPaymentStatus(PaymentStatus.PARTIALLY_PAID);
        long deliveryPendingCount = paymentRepository.countByPaymentStatus(PaymentStatus.DELIVERY_COLLECTION_PENDING);

        return PaymentStatisticsResponse.builder()
                .totalOrdersAmount(totalOrders)
                .totalPaymentsCount(totalPayments)
                .fullOnlineCount(fullOnline)
                .partialOnlineCodCount(partialCod)
                .advanceAmountPayable(advancePayable)
                .advanceAmountReceived(advancePaid)
                .deliveryCashPayable(deliveryPayable)
                .deliveryCashCollected(deliveryCollected)
                .outstandingAmount(outstanding)
                .totalCollectedAmount(totalCollected)
                .totalRefundedAmount(refunded)
                .paidCount(paidCount)
                .failedCount(failedCount)
                .pendingCount(pendingCount)
                .partiallyPaidCount(partiallyPaidCount)
                .deliveryCollectionPendingCount(deliveryPendingCount)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentStatusHistoryResponse> getPaymentStatusHistory(String paymentPublicId) {
        Payment payment = paymentRepository.findByPaymentPublicId(paymentPublicId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with public ID: " + paymentPublicId));

        List<PaymentStatusHistory> history = historyRepository.findByPaymentIdOrderByChangedAtDesc(payment.getId());
        return history.stream()
                .map(h -> PaymentStatusHistoryResponse.builder()
                        .previousStatus(h.getPreviousStatus())
                        .newStatus(h.getNewStatus())
                        .reason(h.getReason())
                        .changedBy(h.getChangedBy())
                        .changedAt(h.getChangedAt())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentTransactionResponse> getPaymentTransactions(String paymentPublicId) {
        Payment payment = paymentRepository.findByPaymentPublicId(paymentPublicId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with public ID: " + paymentPublicId));

        List<PaymentTransaction> transactions = transactionRepository.findByPaymentIdOrderByCreatedAtDesc(payment.getId());
        return transactions.stream()
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
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CodCollectionResponse> getAllCodCollections(Pageable pageable) {
        Page<CodCollection> page = codCollectionRepository.findAllByOrderByCollectedAtDesc(pageable);
        return PageResponse.of(page.map(c -> CodCollectionResponse.builder()
                .collectionPublicId(c.getCollectionPublicId())
                .paymentPublicId(c.getPayment().getPaymentPublicId())
                .orderPublicId(c.getOrderPublicId())
                .collectedAmount(c.getCollectedAmount())
                .totalAmount(c.getPayment().getTotalAmount())
                .advancePaidAmount(c.getPayment().getAdvancePaidAmount())
                .totalDeliveryCollectedAmount(c.getPayment().getDeliveryCollectedAmount())
                .remainingOutstandingAmount(c.getPayment().getOutstandingAmount())
                .paymentStatus(c.getPayment().getPaymentStatus())
                .collectorReference(c.getCollectorReference())
                .deliveryReference(c.getDeliveryReference())
                .idempotencyKey(c.getIdempotencyKey())
                .collectedAt(c.getCollectedAt())
                .build()));
    }

    private void validateOwnership(Payment payment, String customerPublicId) {
        if (customerPublicId != null && !customerPublicId.isBlank()
                && !payment.getCustomerPublicId().equalsIgnoreCase(customerPublicId)) {
            throw new UnauthorizedPaymentAccessException("You are not authorized to view this payment.");
        }
    }

    private PaymentResponse mapToPaymentResponse(Payment payment) {
        List<PaymentTransactionResponse> txList = payment.getTransactions().stream()
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
                .transactions(txList)
                .build();
    }

    private PaymentHistoryResponse mapToHistoryResponse(Payment payment) {
        return PaymentHistoryResponse.builder()
                .paymentPublicId(payment.getPaymentPublicId())
                .orderPublicId(payment.getOrderPublicId())
                .paymentMethod(payment.getPaymentMethod())
                .paymentStatus(payment.getPaymentStatus())
                .totalAmount(payment.getTotalAmount())
                .advancePaidAmount(payment.getAdvancePaidAmount())
                .deliveryCollectedAmount(payment.getDeliveryCollectedAmount())
                .outstandingAmount(payment.getOutstandingAmount())
                .refundedAmount(payment.getRefundedAmount())
                .currency(payment.getCurrency())
                .createdAt(payment.getCreatedAt())
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
}
