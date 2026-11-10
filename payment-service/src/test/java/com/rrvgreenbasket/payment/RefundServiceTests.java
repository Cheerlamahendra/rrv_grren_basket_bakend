package com.rrvgreenbasket.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.payment.dto.request.AdminRefundRequest;
import com.rrvgreenbasket.payment.dto.response.PaymentRefundResponse;
import com.rrvgreenbasket.payment.entity.Payment;
import com.rrvgreenbasket.payment.entity.PaymentTransaction;
import com.rrvgreenbasket.payment.enums.PaymentMethod;
import com.rrvgreenbasket.payment.enums.PaymentStatus;
import com.rrvgreenbasket.payment.enums.RefundStatus;
import com.rrvgreenbasket.payment.enums.TransactionStatus;
import com.rrvgreenbasket.payment.enums.TransactionType;
import com.rrvgreenbasket.payment.exception.RefundException;
import com.rrvgreenbasket.payment.outbox.PaymentEventPublisher;
import com.rrvgreenbasket.payment.repository.PaymentRefundRepository;
import com.rrvgreenbasket.payment.repository.PaymentRepository;
import com.rrvgreenbasket.payment.repository.PaymentStatusHistoryRepository;
import com.rrvgreenbasket.payment.repository.PaymentTransactionRepository;
import com.rrvgreenbasket.payment.service.IdempotencyService;
import com.rrvgreenbasket.payment.service.RazorpayService;
import com.rrvgreenbasket.payment.service.impl.RefundServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Tests — Refund Management Service")
class RefundServiceTests {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentTransactionRepository transactionRepository;

    @Mock
    private PaymentRefundRepository refundRepository;

    @Mock
    private PaymentStatusHistoryRepository historyRepository;

    @Mock
    private RazorpayService razorpayService;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private PaymentEventPublisher eventPublisher;

    private RefundServiceImpl refundService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String paymentId = "pay-uuid-refund-1";
    private final String idempotencyKey = "idemp-refund-1";

    @BeforeEach
    void setUp() {
        refundService = new RefundServiceImpl(
                paymentRepository,
                transactionRepository,
                refundRepository,
                historyRepository,
                razorpayService,
                idempotencyService,
                eventPublisher,
                objectMapper
        );
    }

    @Test
    @DisplayName("Scenario F: Full Refund of Captured Advance Payment")
    void testFullRefundOfCapturedAdvance() {
        Payment payment = Payment.builder()
                .id(1L)
                .paymentPublicId(paymentId)
                .orderPublicId("order-1")
                .paymentMethod(PaymentMethod.PARTIAL_ONLINE_COD)
                .paymentStatus(PaymentStatus.DELIVERY_COLLECTION_PENDING)
                .totalAmount(new BigDecimal("400.00"))
                .advanceAmount(new BigDecimal("200.00"))
                .advancePaidAmount(new BigDecimal("200.00"))
                .deliveryCollectionAmount(new BigDecimal("200.00"))
                .deliveryCollectedAmount(BigDecimal.ZERO)
                .outstandingAmount(new BigDecimal("200.00"))
                .refundedAmount(BigDecimal.ZERO)
                .currency("INR")
                .transactions(new ArrayList<>())
                .build();

        PaymentTransaction capturedTx = PaymentTransaction.builder()
                .transactionType(TransactionType.ONLINE_ADVANCE)
                .transactionStatus(TransactionStatus.SUCCESS)
                .providerPaymentId("pay_rzp_captured_1")
                .providerOrderId("order_rzp_1")
                .amount(new BigDecimal("200.00"))
                .build();
        payment.addTransaction(capturedTx);

        when(idempotencyService.checkAndAcquire(eq(idempotencyKey), eq("REFUND"), eq(paymentId), anyString()))
                .thenReturn(new IdempotencyService.IdempotencyCheck(IdempotencyService.IdempotencyResult.NEW, null));
        when(paymentRepository.findByPaymentPublicId(paymentId)).thenReturn(Optional.of(payment));
        when(razorpayService.createRefund(eq("pay_rzp_captured_1"), eq(new BigDecimal("200.00")), anyString()))
                .thenReturn(new RazorpayService.RazorpayRefundResult("rfnd_123", "pay_rzp_captured_1", 20000L, "processed"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        AdminRefundRequest request = AdminRefundRequest.builder()
                .refundAmount(new BigDecimal("200.00"))
                .refundReason("Customer cancelled order before delivery")
                .idempotencyKey(idempotencyKey)
                .build();

        PaymentRefundResponse response = refundService.processRefund(paymentId, request, "admin@rrv.com");

        assertNotNull(response);
        assertEquals(new BigDecimal("200.00"), response.getRefundAmount());
        assertEquals("rfnd_123", response.getProviderRefundId());
        assertEquals(RefundStatus.PROCESSED, response.getRefundStatus());
        assertEquals(PaymentStatus.REFUNDED, payment.getPaymentStatus());

        verify(eventPublisher).publishEvent(any(Payment.class), eq("REFUND_COMPLETED"), eq("pay_rzp_captured_1"), eq("order_rzp_1"), isNull());
    }

    @Test
    @DisplayName("Partial Refund of Captured Online Amount")
    void testPartialRefund() {
        Payment payment = Payment.builder()
                .id(2L)
                .paymentPublicId(paymentId)
                .paymentMethod(PaymentMethod.ONLINE_FULL)
                .paymentStatus(PaymentStatus.PAID)
                .totalAmount(new BigDecimal("400.00"))
                .advancePaidAmount(new BigDecimal("400.00"))
                .refundedAmount(BigDecimal.ZERO)
                .currency("INR")
                .transactions(new ArrayList<>())
                .build();

        PaymentTransaction capturedTx = PaymentTransaction.builder()
                .transactionType(TransactionType.ONLINE_PAYMENT)
                .transactionStatus(TransactionStatus.SUCCESS)
                .providerPaymentId("pay_rzp_captured_2")
                .amount(new BigDecimal("400.00"))
                .build();
        payment.addTransaction(capturedTx);

        when(idempotencyService.checkAndAcquire(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new IdempotencyService.IdempotencyCheck(IdempotencyService.IdempotencyResult.NEW, null));
        when(paymentRepository.findByPaymentPublicId(paymentId)).thenReturn(Optional.of(payment));
        when(razorpayService.createRefund(eq("pay_rzp_captured_2"), eq(new BigDecimal("100.00")), anyString()))
                .thenReturn(new RazorpayService.RazorpayRefundResult("rfnd_partial", "pay_rzp_captured_2", 10000L, "processed"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        AdminRefundRequest request = AdminRefundRequest.builder()
                .refundAmount(new BigDecimal("100.00"))
                .refundReason("Partial item unavailable")
                .idempotencyKey("idemp-part")
                .build();

        PaymentRefundResponse response = refundService.processRefund(paymentId, request, "admin");

        assertNotNull(response);
        assertEquals(new BigDecimal("100.00"), response.getRefundAmount());
        assertEquals(PaymentStatus.PARTIALLY_REFUNDED, payment.getPaymentStatus());
    }

    @Test
    @DisplayName("Reject refund exceeding refundable online advance balance")
    void testRefundExceedingAvailableOnlineBalanceRejected() {
        Payment payment = Payment.builder()
                .paymentPublicId(paymentId)
                .advancePaidAmount(new BigDecimal("200.00"))
                .refundedAmount(new BigDecimal("150.00")) // Only ₹50 left
                .transactions(new ArrayList<>())
                .build();

        when(idempotencyService.checkAndAcquire(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new IdempotencyService.IdempotencyCheck(IdempotencyService.IdempotencyResult.NEW, null));
        when(paymentRepository.findByPaymentPublicId(paymentId)).thenReturn(Optional.of(payment));

        AdminRefundRequest request = AdminRefundRequest.builder()
                .refundAmount(new BigDecimal("100.00")) // ₹100 > ₹50
                .refundReason("Excess refund attempt")
                .idempotencyKey("idemp-excess")
                .build();

        assertThrows(RefundException.class, () -> refundService.processRefund(paymentId, request, "admin"));
    }
}
