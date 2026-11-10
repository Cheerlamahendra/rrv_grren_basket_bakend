package com.rrvgreenbasket.payment;

import com.rrvgreenbasket.payment.entity.*;
import com.rrvgreenbasket.payment.enums.*;
import com.rrvgreenbasket.payment.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Integration Tests — Flyway Migrations & JPA Repositories")
class RepositoryTests {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentTransactionRepository transactionRepository;

    @Autowired
    private CodCollectionRepository codCollectionRepository;

    @Autowired
    private PaymentRefundRepository refundRepository;

    @Autowired
    private PaymentWebhookEventRepository webhookEventRepository;

    @Autowired
    private PaymentIdempotencyKeyRepository idempotencyRepository;

    @Test
    @DisplayName("Verify Payment entity persistence and aggregate sum queries")
    void testPaymentPersistenceAndAggregates() {
        Payment p1 = Payment.builder()
                .paymentPublicId(UUID.randomUUID().toString())
                .orderPublicId("order-1")
                .customerPublicId("cust-1")
                .paymentMethod(PaymentMethod.ONLINE_FULL)
                .paymentStatus(PaymentStatus.PAID)
                .currency("INR")
                .totalAmount(new BigDecimal("400.00"))
                .advancePercentage(100)
                .advanceAmount(new BigDecimal("400.00"))
                .advancePaidAmount(new BigDecimal("400.00"))
                .deliveryCollectionAmount(BigDecimal.ZERO)
                .deliveryCollectedAmount(BigDecimal.ZERO)
                .outstandingAmount(BigDecimal.ZERO)
                .refundedAmount(BigDecimal.ZERO)
                .build();

        Payment p2 = Payment.builder()
                .paymentPublicId(UUID.randomUUID().toString())
                .orderPublicId("order-2")
                .customerPublicId("cust-2")
                .paymentMethod(PaymentMethod.PARTIAL_ONLINE_COD)
                .paymentStatus(PaymentStatus.DELIVERY_COLLECTION_PENDING)
                .currency("INR")
                .totalAmount(new BigDecimal("400.00"))
                .advancePercentage(50)
                .advanceAmount(new BigDecimal("200.00"))
                .advancePaidAmount(new BigDecimal("200.00"))
                .deliveryCollectionAmount(new BigDecimal("200.00"))
                .deliveryCollectedAmount(BigDecimal.ZERO)
                .outstandingAmount(new BigDecimal("200.00"))
                .refundedAmount(BigDecimal.ZERO)
                .build();

        paymentRepository.save(p1);
        paymentRepository.save(p2);

        assertEquals(2, paymentRepository.countTotalPayments());
        assertEquals(1, paymentRepository.countByPaymentMethod(PaymentMethod.ONLINE_FULL));
        assertEquals(1, paymentRepository.countByPaymentMethod(PaymentMethod.PARTIAL_ONLINE_COD));
        assertEquals(new BigDecimal("800.00"), paymentRepository.sumTotalOrderAmount());
        assertEquals(new BigDecimal("600.00"), paymentRepository.sumTotalAdvancePaidAmount());
        assertEquals(new BigDecimal("200.00"), paymentRepository.sumTotalOutstandingAmount());
    }

    @Test
    @DisplayName("Verify PaymentTransaction persistence and lookups")
    void testTransactionPersistence() {
        Payment payment = Payment.builder()
                .paymentPublicId(UUID.randomUUID().toString())
                .orderPublicId("order-tx-1")
                .customerPublicId("cust-tx-1")
                .paymentMethod(PaymentMethod.ONLINE_FULL)
                .paymentStatus(PaymentStatus.INITIATED)
                .currency("INR")
                .totalAmount(new BigDecimal("100.00"))
                .advancePercentage(100)
                .advanceAmount(new BigDecimal("100.00"))
                .advancePaidAmount(BigDecimal.ZERO)
                .deliveryCollectionAmount(BigDecimal.ZERO)
                .deliveryCollectedAmount(BigDecimal.ZERO)
                .outstandingAmount(new BigDecimal("100.00"))
                .refundedAmount(BigDecimal.ZERO)
                .build();

        Payment savedPayment = paymentRepository.save(payment);

        PaymentTransaction tx = PaymentTransaction.builder()
                .transactionPublicId(UUID.randomUUID().toString())
                .payment(savedPayment)
                .transactionType(TransactionType.ONLINE_PAYMENT)
                .transactionStatus(TransactionStatus.INITIATED)
                .amount(new BigDecimal("100.00"))
                .currency("INR")
                .provider(PaymentProvider.RAZORPAY)
                .providerOrderId("order_rzp_lookup_test")
                .providerPaymentId("pay_rzp_lookup_test")
                .build();

        transactionRepository.save(tx);

        Optional<PaymentTransaction> found = transactionRepository.findByProviderOrderId("order_rzp_lookup_test");
        assertTrue(found.isPresent());
        assertEquals("pay_rzp_lookup_test", found.get().getProviderPaymentId());
    }

    @Test
    @DisplayName("Verify Webhook event deduplication constraint")
    void testWebhookEventDeduplication() {
        PaymentWebhookEvent evt = PaymentWebhookEvent.builder()
                .provider(PaymentProvider.RAZORPAY)
                .providerEventId("evt_unique_12345")
                .eventType("payment.captured")
                .eventPayload("{}")
                .processingStatus(WebhookProcessingStatus.PROCESSED)
                .build();

        webhookEventRepository.save(evt);

        assertTrue(webhookEventRepository.existsByProviderAndProviderEventId(PaymentProvider.RAZORPAY, "evt_unique_12345"));
        assertFalse(webhookEventRepository.existsByProviderAndProviderEventId(PaymentProvider.RAZORPAY, "evt_non_existent"));
    }
}
