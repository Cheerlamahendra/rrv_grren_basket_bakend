package com.rrvgreenbasket.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.payment.dto.request.CodCollectionRequest;
import com.rrvgreenbasket.payment.dto.response.CodCollectionResponse;
import com.rrvgreenbasket.payment.entity.CodCollection;
import com.rrvgreenbasket.payment.entity.Payment;
import com.rrvgreenbasket.payment.entity.PaymentStatusHistory;
import com.rrvgreenbasket.payment.entity.PaymentTransaction;
import com.rrvgreenbasket.payment.enums.PaymentMethod;
import com.rrvgreenbasket.payment.enums.PaymentProvider;
import com.rrvgreenbasket.payment.enums.PaymentStatus;
import com.rrvgreenbasket.payment.enums.TransactionStatus;
import com.rrvgreenbasket.payment.enums.TransactionType;
import com.rrvgreenbasket.payment.exception.CodCollectionException;
import com.rrvgreenbasket.payment.exception.PaymentNotFoundException;
import com.rrvgreenbasket.payment.outbox.PaymentEventPublisher;
import com.rrvgreenbasket.payment.repository.CodCollectionRepository;
import com.rrvgreenbasket.payment.repository.PaymentRepository;
import com.rrvgreenbasket.payment.repository.PaymentStatusHistoryRepository;
import com.rrvgreenbasket.payment.service.CodCollectionService;
import com.rrvgreenbasket.payment.service.IdempotencyService;
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
public class CodCollectionServiceImpl implements CodCollectionService {

    private final PaymentRepository paymentRepository;
    private final CodCollectionRepository codCollectionRepository;
    private final PaymentStatusHistoryRepository historyRepository;
    private final IdempotencyService idempotencyService;
    private final PaymentEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public CodCollectionResponse collectCod(String paymentPublicId, CodCollectionRequest request, String callerReference) {
        log.info("Processing COD delivery cash collection for payment '{}', order '{}', amount ₹{}, collector '{}', idempotencyKey '{}'",
                paymentPublicId, request.getOrderPublicId(), request.getCollectedAmount(), request.getCollectorReference(), request.getIdempotencyKey());

        // 1. Idempotency Check
        String payloadJson = serializeRequest(request);
        IdempotencyService.IdempotencyCheck idempCheck = idempotencyService.checkAndAcquire(
                request.getIdempotencyKey(), "COD_COLLECTION", paymentPublicId, payloadJson);

        if (idempCheck.result() == IdempotencyService.IdempotencyResult.ALREADY_COMPLETED) {
            log.info("COD collection already completed for idempotency key '{}'. Returning existing record.", request.getIdempotencyKey());
            return returnExistingCollection(idempCheck.responseReference(), request.getIdempotencyKey());
        }

        try {
            // 2. Validate Payment
            Payment payment = paymentRepository.findByPaymentPublicId(paymentPublicId)
                    .orElseThrow(() -> new PaymentNotFoundException("Payment record not found for public ID: " + paymentPublicId));

            // Rule 1: Payment belongs to specified order
            if (!payment.getOrderPublicId().equals(request.getOrderPublicId())) {
                throw new CodCollectionException(String.format("Payment order mismatch: payment belongs to order '%s', request specified '%s'",
                        payment.getOrderPublicId(), request.getOrderPublicId()));
            }

            // Rule 2: Payment method is PARTIAL_ONLINE_COD
            if (payment.getPaymentMethod() != PaymentMethod.PARTIAL_ONLINE_COD) {
                throw new CodCollectionException("COD cash collection is only valid for PARTIAL_ONLINE_COD payments. Current method: "
                        + payment.getPaymentMethod());
            }

            // Rule 3: Advance payment is captured successfully
            if (payment.getPaymentStatus() == PaymentStatus.ADVANCE_PAYMENT_INITIATED
                    || payment.getPaymentStatus() == PaymentStatus.ADVANCE_PAYMENT_PENDING
                    || payment.getPaymentStatus() == PaymentStatus.PENDING
                    || payment.getPaymentStatus() == PaymentStatus.INITIATED) {
                throw new CodCollectionException("Cannot record COD delivery collection before the online advance payment is captured.");
            }

            // Rule 4: Order/Payment has not been cancelled or refunded
            if (payment.getPaymentStatus() == PaymentStatus.CANCELLED
                    || payment.getPaymentStatus() == PaymentStatus.REFUNDED
                    || payment.getPaymentStatus() == PaymentStatus.FAILED) {
                throw new CodCollectionException("Cannot collect cash for an order with status: " + payment.getPaymentStatus());
            }

            // Rule 5: Collection does not exceed outstanding balance
            BigDecimal collectedAmount = request.getCollectedAmount();
            BigDecimal currentOutstanding = payment.getOutstandingAmount();

            if (collectedAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new CodCollectionException("Collected amount must be greater than zero.");
            }

            if (collectedAmount.compareTo(currentOutstanding) > 0) {
                throw new CodCollectionException(String.format(
                        "Collection amount ₹%s exceeds the current outstanding balance ₹%s.",
                        collectedAmount, currentOutstanding));
            }

            // 3. Update payment balances and status
            String previousStatus = payment.getPaymentStatus().name();
            BigDecimal newDeliveryCollected = payment.getDeliveryCollectedAmount().add(collectedAmount);
            BigDecimal newOutstanding = payment.getTotalAmount()
                    .subtract(payment.getAdvancePaidAmount())
                    .subtract(newDeliveryCollected);

            payment.setDeliveryCollectedAmount(newDeliveryCollected);
            payment.setOutstandingAmount(newOutstanding);

            boolean fullyPaid = newOutstanding.compareTo(BigDecimal.ZERO) == 0;
            if (fullyPaid) {
                payment.setPaymentStatus(PaymentStatus.PAID);
            } else {
                payment.setPaymentStatus(PaymentStatus.PARTIALLY_PAID);
            }

            // 4. Record COD Collection record
            String collectionPublicId = UUID.randomUUID().toString();
            Instant now = Instant.now();

            CodCollection collection = CodCollection.builder()
                    .collectionPublicId(collectionPublicId)
                    .payment(payment)
                    .orderPublicId(payment.getOrderPublicId())
                    .collectedAmount(collectedAmount)
                    .collectorReference(request.getCollectorReference())
                    .deliveryReference(request.getDeliveryReference())
                    .collectionStatus("COLLECTED")
                    .idempotencyKey(request.getIdempotencyKey())
                    .collectedAt(now)
                    .build();
            codCollectionRepository.save(collection);

            // 5. Record Cash Transaction
            PaymentTransaction transaction = PaymentTransaction.builder()
                    .transactionPublicId(UUID.randomUUID().toString())
                    .payment(payment)
                    .transactionType(TransactionType.COD_COLLECTION)
                    .transactionStatus(TransactionStatus.SUCCESS)
                    .amount(collectedAmount)
                    .currency(payment.getCurrency())
                    .provider(PaymentProvider.CASH)
                    .providerReference(request.getDeliveryReference())
                    .build();
            payment.addTransaction(transaction);

            Payment updatedPayment = paymentRepository.save(payment);

            // 6. Record Status History
            String reason = String.format("COD cash collection of ₹%s recorded by %s (deliveryRef: %s)",
                    collectedAmount, request.getCollectorReference(), request.getDeliveryReference());
            recordStatusHistory(updatedPayment, previousStatus, updatedPayment.getPaymentStatus().name(), reason, callerReference);

            // 7. Complete Idempotency
            idempotencyService.complete(request.getIdempotencyKey(), collectionPublicId);

            // 8. Publish Outbox Events
            eventPublisher.publishEvent(updatedPayment, "COD_PAYMENT_COLLECTED", null, null, null);
            if (fullyPaid) {
                eventPublisher.publishEvent(updatedPayment, "PAYMENT_COMPLETED", null, null, null);
            }

            log.info("COD cash collection completed successfully: collectionPublicId={}, collectedAmount=₹{}, remainingOutstanding=₹{}, newStatus={}",
                    collectionPublicId, collectedAmount, newOutstanding, updatedPayment.getPaymentStatus());

            return CodCollectionResponse.builder()
                    .collectionPublicId(collectionPublicId)
                    .paymentPublicId(payment.getPaymentPublicId())
                    .orderPublicId(payment.getOrderPublicId())
                    .collectedAmount(collectedAmount)
                    .totalAmount(payment.getTotalAmount())
                    .advancePaidAmount(payment.getAdvancePaidAmount())
                    .totalDeliveryCollectedAmount(newDeliveryCollected)
                    .remainingOutstandingAmount(newOutstanding)
                    .paymentStatus(payment.getPaymentStatus())
                    .collectorReference(request.getCollectorReference())
                    .deliveryReference(request.getDeliveryReference())
                    .idempotencyKey(request.getIdempotencyKey())
                    .collectedAt(now)
                    .build();

        } catch (Exception ex) {
            idempotencyService.fail(request.getIdempotencyKey());
            log.error("Failed to process COD collection for payment '{}': {}", paymentPublicId, ex.getMessage());
            throw ex;
        }
    }

    private CodCollectionResponse returnExistingCollection(String collectionPublicId, String idempotencyKey) {
        Optional<CodCollection> colOpt = (collectionPublicId != null)
                ? codCollectionRepository.findByCollectionPublicId(collectionPublicId)
                : codCollectionRepository.findByIdempotencyKey(idempotencyKey);

        if (colOpt.isPresent()) {
            CodCollection c = colOpt.get();
            Payment p = c.getPayment();
            return CodCollectionResponse.builder()
                    .collectionPublicId(c.getCollectionPublicId())
                    .paymentPublicId(p.getPaymentPublicId())
                    .orderPublicId(p.getOrderPublicId())
                    .collectedAmount(c.getCollectedAmount())
                    .totalAmount(p.getTotalAmount())
                    .advancePaidAmount(p.getAdvancePaidAmount())
                    .totalDeliveryCollectedAmount(p.getDeliveryCollectedAmount())
                    .remainingOutstandingAmount(p.getOutstandingAmount())
                    .paymentStatus(p.getPaymentStatus())
                    .collectorReference(c.getCollectorReference())
                    .deliveryReference(c.getDeliveryReference())
                    .idempotencyKey(c.getIdempotencyKey())
                    .collectedAt(c.getCollectedAt())
                    .build();
        }

        throw new CodCollectionException("Idempotent record could not be found for key: " + idempotencyKey);
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

    private String serializeRequest(CodCollectionRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            return request.toString();
        }
    }
}
