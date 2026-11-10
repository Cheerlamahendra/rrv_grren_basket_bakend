package com.rrvgreenbasket.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.payment.entity.Payment;
import com.rrvgreenbasket.payment.entity.PaymentTransaction;
import com.rrvgreenbasket.payment.entity.PaymentWebhookEvent;
import com.rrvgreenbasket.payment.enums.PaymentMethod;
import com.rrvgreenbasket.payment.enums.PaymentProvider;
import com.rrvgreenbasket.payment.enums.PaymentStatus;
import com.rrvgreenbasket.payment.exception.InvalidSignatureException;
import com.rrvgreenbasket.payment.outbox.PaymentEventPublisher;
import com.rrvgreenbasket.payment.repository.PaymentRepository;
import com.rrvgreenbasket.payment.repository.PaymentStatusHistoryRepository;
import com.rrvgreenbasket.payment.repository.PaymentTransactionRepository;
import com.rrvgreenbasket.payment.repository.PaymentWebhookEventRepository;
import com.rrvgreenbasket.payment.service.RazorpayService;
import com.rrvgreenbasket.payment.service.impl.WebhookServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Tests — Razorpay Webhook Inbox & Processing")
class WebhookServiceTests {

    @Mock
    private PaymentWebhookEventRepository webhookEventRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentTransactionRepository transactionRepository;

    @Mock
    private PaymentStatusHistoryRepository historyRepository;

    @Mock
    private RazorpayService razorpayService;

    @Mock
    private PaymentEventPublisher eventPublisher;

    private WebhookServiceImpl webhookService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        webhookService = new WebhookServiceImpl(
                webhookEventRepository,
                paymentRepository,
                transactionRepository,
                historyRepository,
                razorpayService,
                eventPublisher,
                objectMapper
        );
    }

    @Test
    @DisplayName("Scenario G: Duplicate Webhook arrives multiple times -> Processed only once")
    void testDuplicateWebhookIgnored() {
        String payload = "{\"event_id\":\"evt_duplicate_1\",\"event\":\"payment.captured\",\"payload\":{}}";
        when(razorpayService.verifyWebhookSignature(payload, "valid_sig")).thenReturn(true);
        // First check returns true (already exists in DB inbox)
        when(webhookEventRepository.existsByProviderAndProviderEventId(PaymentProvider.RAZORPAY, "evt_duplicate_1"))
                .thenReturn(true);

        webhookService.processRazorpayWebhook(payload, "valid_sig");

        // Verify no transition or event publishing took place
        verify(paymentRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Reject webhook with invalid signature")
    void testInvalidWebhookSignatureRejected() {
        String payload = "{\"event_id\":\"evt_bad_sig\",\"event\":\"payment.captured\"}";
        when(razorpayService.verifyWebhookSignature(payload, "tampered_sig")).thenReturn(false);

        assertThrows(InvalidSignatureException.class,
                () -> webhookService.processRazorpayWebhook(payload, "tampered_sig"));
    }

    @Test
    @DisplayName("Out-of-Order Webhook: payment.failed arriving after payment.captured does not downgrade")
    void testOutOfOrderLateFailureDoesNotDowngradeCaptured() {
        String payload = """
                {
                  "event_id": "evt_late_fail_1",
                  "event": "payment.failed",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "pay_late_1",
                        "order_id": "order_captured_1"
                      }
                    }
                  }
                }
                """;

        when(razorpayService.verifyWebhookSignature(payload, "valid_sig")).thenReturn(true);
        when(webhookEventRepository.existsByProviderAndProviderEventId(PaymentProvider.RAZORPAY, "evt_late_fail_1")).thenReturn(false);
        when(webhookEventRepository.save(any(PaymentWebhookEvent.class))).thenAnswer(i -> i.getArgument(0));

        PaymentTransaction tx = PaymentTransaction.builder()
                .providerOrderId("order_captured_1")
                .providerPaymentId("pay_late_1")
                .build();

        Payment payment = Payment.builder()
                .paymentPublicId("pay-uuid-captured")
                .paymentStatus(PaymentStatus.PAID) // Already captured and paid!
                .build();
        tx.setPayment(payment);

        when(transactionRepository.findByProviderOrderId("order_captured_1")).thenReturn(Optional.of(tx));

        webhookService.processRazorpayWebhook(payload, "valid_sig");

        // Status must remain PAID! Not downgraded to FAILED!
        assertEquals(PaymentStatus.PAID, payment.getPaymentStatus());
        verify(eventPublisher, never()).publishEvent(any(), eq("PAYMENT_FAILED"), any(), any(), any());
    }

    @Test
    @DisplayName("Process payment.captured webhook for partial COD order -> DELIVERY_COLLECTION_PENDING")
    void testProcessPaymentCapturedForPartialCod() {
        String payload = """
                {
                  "event_id": "evt_cap_part_1",
                  "event": "payment.captured",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "pay_rzp_adv_1",
                        "order_id": "order_rzp_adv_1",
                        "amount": 20000
                      }
                    }
                  }
                }
                """;

        when(razorpayService.verifyWebhookSignature(payload, "valid_sig")).thenReturn(true);
        when(webhookEventRepository.existsByProviderAndProviderEventId(PaymentProvider.RAZORPAY, "evt_cap_part_1")).thenReturn(false);
        when(webhookEventRepository.save(any(PaymentWebhookEvent.class))).thenAnswer(i -> i.getArgument(0));

        Payment payment = Payment.builder()
                .paymentPublicId("pay-part-cap")
                .paymentMethod(PaymentMethod.PARTIAL_ONLINE_COD)
                .paymentStatus(PaymentStatus.ADVANCE_PAYMENT_INITIATED)
                .totalAmount(new BigDecimal("400.00"))
                .advancePercentage(50)
                .advanceAmount(new BigDecimal("200.00"))
                .advancePaidAmount(BigDecimal.ZERO)
                .deliveryCollectionAmount(new BigDecimal("200.00"))
                .deliveryCollectedAmount(BigDecimal.ZERO)
                .outstandingAmount(new BigDecimal("400.00"))
                .transactions(new ArrayList<>())
                .build();

        PaymentTransaction tx = PaymentTransaction.builder()
                .providerOrderId("order_rzp_adv_1")
                .payment(payment)
                .build();

        when(transactionRepository.findByProviderOrderId("order_rzp_adv_1")).thenReturn(Optional.of(tx));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        webhookService.processRazorpayWebhook(payload, "valid_sig");

        assertEquals(PaymentStatus.DELIVERY_COLLECTION_PENDING, payment.getPaymentStatus());
        assertEquals(new BigDecimal("200.00"), payment.getAdvancePaidAmount());
        assertEquals(new BigDecimal("200.00"), payment.getOutstandingAmount());

        verify(eventPublisher).publishEvent(any(Payment.class), eq("PARTIAL_PAYMENT_CAPTURED"), eq("pay_rzp_adv_1"), eq("order_rzp_adv_1"), isNull());
    }
}
