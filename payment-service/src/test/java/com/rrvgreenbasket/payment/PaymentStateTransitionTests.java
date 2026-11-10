package com.rrvgreenbasket.payment;

import com.rrvgreenbasket.payment.client.OrderServiceClient;
import com.rrvgreenbasket.payment.client.dto.OrderDetailsDto;
import com.rrvgreenbasket.payment.config.PartialCodProperties;
import com.rrvgreenbasket.payment.dto.request.OnlinePaymentInitiateRequest;
import com.rrvgreenbasket.payment.dto.request.PartialCodPaymentInitiateRequest;
import com.rrvgreenbasket.payment.dto.request.PaymentVerificationRequest;
import com.rrvgreenbasket.payment.dto.response.OnlinePaymentInitiateResponse;
import com.rrvgreenbasket.payment.dto.response.PartialCodPaymentInitiateResponse;
import com.rrvgreenbasket.payment.dto.response.PaymentResponse;
import com.rrvgreenbasket.payment.entity.Payment;
import com.rrvgreenbasket.payment.entity.PaymentStatusHistory;
import com.rrvgreenbasket.payment.enums.PaymentMethod;
import com.rrvgreenbasket.payment.enums.PaymentStatus;
import com.rrvgreenbasket.payment.enums.TransactionType;
import com.rrvgreenbasket.payment.exception.InvalidSignatureException;
import com.rrvgreenbasket.payment.exception.UnauthorizedPaymentAccessException;
import com.rrvgreenbasket.payment.outbox.PaymentEventPublisher;
import com.rrvgreenbasket.payment.repository.PaymentRepository;
import com.rrvgreenbasket.payment.repository.PaymentStatusHistoryRepository;
import com.rrvgreenbasket.payment.repository.PaymentTransactionRepository;
import com.rrvgreenbasket.payment.service.RazorpayService;
import com.rrvgreenbasket.payment.service.impl.OnlinePaymentServiceImpl;
import com.rrvgreenbasket.payment.service.impl.PartialPaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Tests — Payment State Transitions & Workflows")
class PaymentStateTransitionTests {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentTransactionRepository transactionRepository;

    @Mock
    private PaymentStatusHistoryRepository historyRepository;

    @Mock
    private OrderServiceClient orderServiceClient;

    @Mock
    private RazorpayService razorpayService;

    @Mock
    private PaymentEventPublisher eventPublisher;

    private OnlinePaymentServiceImpl onlinePaymentService;
    private PartialPaymentServiceImpl partialPaymentService;
    private PartialCodProperties partialCodProperties;

    private final String orderId = "order-uuid-100";
    private final String customerId = "customer-uuid-200";

    @BeforeEach
    void setUp() {
        partialCodProperties = new PartialCodProperties();
        partialCodProperties.setAdvancePercentage(50);
        partialCodProperties.setDeliveryCollectionPercentage(50);
        partialCodProperties.setEnabled(true);

        onlinePaymentService = new OnlinePaymentServiceImpl(
                paymentRepository,
                transactionRepository,
                historyRepository,
                orderServiceClient,
                razorpayService,
                eventPublisher
        );

        partialPaymentService = new PartialPaymentServiceImpl(
                paymentRepository,
                transactionRepository,
                historyRepository,
                orderServiceClient,
                razorpayService,
                partialCodProperties,
                eventPublisher
        );
    }

    @Test
    @DisplayName("Scenario A: Initiate Full Online Payment (100% via Razorpay)")
    void testInitiateFullOnlinePayment() {
        OrderDetailsDto mockOrder = OrderDetailsDto.builder()
                .orderPublicId(orderId)
                .customerPublicId(customerId)
                .totalAmount(new BigDecimal("400.00"))
                .currency("INR")
                .orderStatus("PENDING_PAYMENT")
                .paymentEligible(true)
                .paymentMethod("ONLINE_FULL")
                .build();

        when(orderServiceClient.getOrderForPayment(orderId)).thenReturn(mockOrder);
        when(paymentRepository.findByOrderPublicId(orderId)).thenReturn(Optional.empty());
        when(razorpayService.createOrder(eq(new BigDecimal("400.00")), anyString(), anyMap()))
                .thenReturn(new RazorpayService.RazorpayOrderResult("rzp_order_400", 40000L, "INR", "rzp_test_key"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OnlinePaymentInitiateRequest request = new OnlinePaymentInitiateRequest(orderId);
        OnlinePaymentInitiateResponse response = onlinePaymentService.initiatePayment(request, customerId);

        assertNotNull(response);
        assertEquals("rzp_order_400", response.getRazorpayOrderId());
        assertEquals(40000L, response.getAmount());
        assertEquals(PaymentMethod.ONLINE_FULL, response.getPaymentMethod());

        verify(eventPublisher, times(1)).publishEvent(any(Payment.class), eq("PAYMENT_INITIATED"), isNull(), eq("rzp_order_400"), isNull());
    }

    @Test
    @DisplayName("Scenario B: Initiate 50% Advance Partial COD Payment")
    void testInitiatePartialCodPayment() {
        OrderDetailsDto mockOrder = OrderDetailsDto.builder()
                .orderPublicId(orderId)
                .customerPublicId(customerId)
                .totalAmount(new BigDecimal("400.00"))
                .currency("INR")
                .orderStatus("PENDING_PAYMENT")
                .paymentEligible(true)
                .paymentMethod("PARTIAL_ONLINE_COD")
                .build();

        when(orderServiceClient.getOrderForPayment(orderId)).thenReturn(mockOrder);
        when(paymentRepository.findByOrderPublicId(orderId)).thenReturn(Optional.empty());
        // Verify Razorpay order is created for ONLY the 50% advance: ₹200.00 (20000 paise)
        when(razorpayService.createOrder(eq(new BigDecimal("200.00")), anyString(), anyMap()))
                .thenReturn(new RazorpayService.RazorpayOrderResult("rzp_order_200", 20000L, "INR", "rzp_test_key"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PartialCodPaymentInitiateRequest request = new PartialCodPaymentInitiateRequest(orderId);
        PartialCodPaymentInitiateResponse response = partialPaymentService.initiatePartialCod(request, customerId);

        assertNotNull(response);
        assertEquals("rzp_order_200", response.getRazorpayOrderId());
        assertEquals(20000L, response.getRazorpayAmount());
        assertEquals(new BigDecimal("400.00"), response.getTotalAmount());
        assertEquals(new BigDecimal("200.00"), response.getAdvanceAmount());
        assertEquals(new BigDecimal("200.00"), response.getDeliveryCollectionAmount());
        assertEquals(new BigDecimal("400.00"), response.getOutstandingAmount());
        assertEquals(PaymentMethod.PARTIAL_ONLINE_COD, response.getPaymentMethod());

        verify(eventPublisher, times(1)).publishEvent(any(Payment.class), eq("PAYMENT_INITIATED"), isNull(), eq("rzp_order_200"), isNull());
    }

    @Test
    @DisplayName("Verify Full Online Payment -> Transitions to PAID")
    void testVerifyFullOnlinePayment() {
        Payment mockPayment = Payment.builder()
                .id(1L)
                .paymentPublicId("pay-uuid-1")
                .orderPublicId(orderId)
                .customerPublicId(customerId)
                .paymentMethod(PaymentMethod.ONLINE_FULL)
                .paymentStatus(PaymentStatus.INITIATED)
                .totalAmount(new BigDecimal("400.00"))
                .advancePercentage(100)
                .advanceAmount(new BigDecimal("400.00"))
                .advancePaidAmount(BigDecimal.ZERO)
                .deliveryCollectionAmount(BigDecimal.ZERO)
                .deliveryCollectedAmount(BigDecimal.ZERO)
                .outstandingAmount(new BigDecimal("400.00"))
                .currency("INR")
                .build();

        when(paymentRepository.findByPaymentPublicId("pay-uuid-1")).thenReturn(Optional.of(mockPayment));
        when(razorpayService.verifyCheckoutSignature("rzp_order_1", "rzp_pay_1", "valid_sig")).thenReturn(true);
        when(razorpayService.fetchPayment("rzp_pay_1")).thenReturn(
                new RazorpayService.RazorpayPaymentDetails("rzp_pay_1", "rzp_order_1", "captured", 40000L, "INR", true)
        );
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentVerificationRequest verifyRequest = new PaymentVerificationRequest("pay-uuid-1", "rzp_order_1", "rzp_pay_1", "valid_sig");
        PaymentResponse response = onlinePaymentService.verifyPayment(verifyRequest, customerId);

        assertNotNull(response);
        assertEquals(PaymentStatus.PAID, response.getPaymentStatus());
        assertEquals(new BigDecimal("400.00"), response.getAdvancePaidAmount());
        assertEquals(0, response.getOutstandingAmount().compareTo(BigDecimal.ZERO));

        verify(eventPublisher).publishEvent(any(Payment.class), eq("PAYMENT_CAPTURED"), eq("rzp_pay_1"), eq("rzp_order_1"), isNull());
        verify(eventPublisher).publishEvent(any(Payment.class), eq("PAYMENT_COMPLETED"), eq("rzp_pay_1"), eq("rzp_order_1"), isNull());
    }

    @Test
    @DisplayName("Verify Partial COD Advance -> Transitions to DELIVERY_COLLECTION_PENDING")
    void testVerifyPartialCodAdvancePayment() {
        Payment mockPayment = Payment.builder()
                .id(2L)
                .paymentPublicId("pay-uuid-2")
                .orderPublicId(orderId)
                .customerPublicId(customerId)
                .paymentMethod(PaymentMethod.PARTIAL_ONLINE_COD)
                .paymentStatus(PaymentStatus.ADVANCE_PAYMENT_INITIATED)
                .totalAmount(new BigDecimal("400.00"))
                .advancePercentage(50)
                .advanceAmount(new BigDecimal("200.00"))
                .advancePaidAmount(BigDecimal.ZERO)
                .deliveryCollectionAmount(new BigDecimal("200.00"))
                .deliveryCollectedAmount(BigDecimal.ZERO)
                .outstandingAmount(new BigDecimal("400.00"))
                .currency("INR")
                .build();

        when(paymentRepository.findByPaymentPublicId("pay-uuid-2")).thenReturn(Optional.of(mockPayment));
        when(razorpayService.verifyCheckoutSignature("rzp_order_adv", "rzp_pay_adv", "valid_sig")).thenReturn(true);
        when(razorpayService.fetchPayment("rzp_pay_adv")).thenReturn(
                new RazorpayService.RazorpayPaymentDetails("rzp_pay_adv", "rzp_order_adv", "captured", 20000L, "INR", true)
        );
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentVerificationRequest verifyRequest = new PaymentVerificationRequest("pay-uuid-2", "rzp_order_adv", "rzp_pay_adv", "valid_sig");
        PaymentResponse response = onlinePaymentService.verifyPayment(verifyRequest, customerId);

        assertNotNull(response);
        // Status must NOT be PAID! It must be DELIVERY_COLLECTION_PENDING!
        assertEquals(PaymentStatus.DELIVERY_COLLECTION_PENDING, response.getPaymentStatus());
        assertEquals(new BigDecimal("200.00"), response.getAdvancePaidAmount());
        assertEquals(new BigDecimal("200.00"), response.getOutstandingAmount());

        verify(eventPublisher).publishEvent(any(Payment.class), eq("PARTIAL_PAYMENT_CAPTURED"), eq("rzp_pay_adv"), eq("rzp_order_adv"), isNull());
    }

    @Test
    @DisplayName("Invalid signature throws InvalidSignatureException and marks FAILED")
    void testInvalidSignatureThrowsException() {
        Payment mockPayment = Payment.builder()
                .id(3L)
                .paymentPublicId("pay-uuid-3")
                .orderPublicId(orderId)
                .customerPublicId(customerId)
                .paymentMethod(PaymentMethod.ONLINE_FULL)
                .paymentStatus(PaymentStatus.INITIATED)
                .totalAmount(new BigDecimal("400.00"))
                .advancePercentage(100)
                .advanceAmount(new BigDecimal("400.00"))
                .advancePaidAmount(BigDecimal.ZERO)
                .deliveryCollectionAmount(BigDecimal.ZERO)
                .deliveryCollectedAmount(BigDecimal.ZERO)
                .outstandingAmount(new BigDecimal("400.00"))
                .currency("INR")
                .build();

        when(paymentRepository.findByPaymentPublicId("pay-uuid-3")).thenReturn(Optional.of(mockPayment));
        when(razorpayService.verifyCheckoutSignature("rzp_order_1", "rzp_pay_1", "bad_sig")).thenReturn(false);

        PaymentVerificationRequest verifyRequest = new PaymentVerificationRequest("pay-uuid-3", "rzp_order_1", "rzp_pay_1", "bad_sig");

        assertThrows(InvalidSignatureException.class, () -> onlinePaymentService.verifyPayment(verifyRequest, customerId));
        assertEquals(PaymentStatus.FAILED, mockPayment.getPaymentStatus());
    }

    @Test
    @DisplayName("Customer Ownership Validation: unauthorized customer rejected")
    void testUnauthorizedCustomerAccessRejected() {
        Payment mockPayment = Payment.builder()
                .paymentPublicId("pay-uuid-4")
                .customerPublicId("actual-customer-1")
                .build();

        when(paymentRepository.findByPaymentPublicId("pay-uuid-4")).thenReturn(Optional.of(mockPayment));
        PaymentVerificationRequest verifyRequest = new PaymentVerificationRequest("pay-uuid-4", "o1", "p1", "s1");

        assertThrows(UnauthorizedPaymentAccessException.class,
                () -> onlinePaymentService.verifyPayment(verifyRequest, "intruder-customer-2"));
    }
}
