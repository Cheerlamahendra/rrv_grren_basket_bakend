package com.rrvgreenbasket.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.payment.controller.AdminPaymentController;
import com.rrvgreenbasket.payment.controller.InternalPaymentController;
import com.rrvgreenbasket.payment.controller.PaymentController;
import com.rrvgreenbasket.payment.controller.RazorpayWebhookController;
import com.rrvgreenbasket.payment.dto.request.CodCollectionRequest;
import com.rrvgreenbasket.payment.dto.request.OnlinePaymentInitiateRequest;
import com.rrvgreenbasket.payment.dto.request.PartialCodPaymentInitiateRequest;
import com.rrvgreenbasket.payment.dto.request.PaymentVerificationRequest;
import com.rrvgreenbasket.payment.dto.response.*;
import com.rrvgreenbasket.payment.enums.PaymentMethod;
import com.rrvgreenbasket.payment.enums.PaymentStatus;
import com.rrvgreenbasket.payment.exception.GlobalExceptionHandler;
import com.rrvgreenbasket.payment.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Web MVC Tests — Payment Controllers")
class CustomerAndAdminControllerTests {

    private MockMvc paymentMockMvc;
    private MockMvc internalMockMvc;
    private MockMvc adminMockMvc;
    private MockMvc webhookMockMvc;

    @Mock
    private OnlinePaymentService onlinePaymentService;

    @Mock
    private PartialPaymentService partialPaymentService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private CodCollectionService codCollectionService;

    @Mock
    private RefundService refundService;

    @Mock
    private WebhookService webhookService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        PaymentController paymentController = new PaymentController(
                onlinePaymentService, partialPaymentService, paymentService);
        paymentMockMvc = MockMvcBuilders.standaloneSetup(paymentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        InternalPaymentController internalController = new InternalPaymentController(codCollectionService);
        internalMockMvc = MockMvcBuilders.standaloneSetup(internalController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        AdminPaymentController adminController = new AdminPaymentController(paymentService, refundService);
        adminMockMvc = MockMvcBuilders.standaloneSetup(adminController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        RazorpayWebhookController webhookController = new RazorpayWebhookController(webhookService);
        webhookMockMvc = MockMvcBuilders.standaloneSetup(webhookController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/payments/online/initiate - Returns 201 with checkout details")
    void testInitiateOnlinePaymentMvc() throws Exception {
        OnlinePaymentInitiateResponse response = OnlinePaymentInitiateResponse.builder()
                .paymentPublicId("pay-uuid-mvc-1")
                .razorpayOrderId("order_mvc_400")
                .razorpayKeyId("rzp_test_key")
                .amount(40000L)
                .currency("INR")
                .paymentMethod(PaymentMethod.ONLINE_FULL)
                .build();

        when(onlinePaymentService.initiatePayment(any(OnlinePaymentInitiateRequest.class), any())).thenReturn(response);

        OnlinePaymentInitiateRequest req = new OnlinePaymentInitiateRequest("order-100");

        paymentMockMvc.perform(post("/api/payments/online/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.paymentPublicId").value("pay-uuid-mvc-1"))
                .andExpect(jsonPath("$.data.razorpayOrderId").value("order_mvc_400"))
                .andExpect(jsonPath("$.data.amount").value(40000));
    }

    @Test
    @DisplayName("POST /api/payments/partial-cod/initiate - Returns 201 with 50% breakdown")
    void testInitiatePartialCodPaymentMvc() throws Exception {
        PartialCodPaymentInitiateResponse response = PartialCodPaymentInitiateResponse.builder()
                .paymentPublicId("pay-uuid-mvc-2")
                .orderPublicId("order-200")
                .paymentMethod(PaymentMethod.PARTIAL_ONLINE_COD)
                .totalAmount(new BigDecimal("400.00"))
                .advancePercentage(50)
                .advanceAmount(new BigDecimal("200.00"))
                .deliveryCollectionAmount(new BigDecimal("200.00"))
                .outstandingAmount(new BigDecimal("400.00"))
                .currency("INR")
                .paymentStatus(PaymentStatus.ADVANCE_PAYMENT_INITIATED)
                .razorpayOrderId("order_mvc_200")
                .razorpayKeyId("rzp_test_key")
                .razorpayAmount(20000L)
                .build();

        when(partialPaymentService.initiatePartialCod(any(PartialCodPaymentInitiateRequest.class), any())).thenReturn(response);

        PartialCodPaymentInitiateRequest req = new PartialCodPaymentInitiateRequest("order-200");

        paymentMockMvc.perform(post("/api/payments/partial-cod/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.paymentMethod").value("PARTIAL_ONLINE_COD"))
                .andExpect(jsonPath("$.data.advanceAmount").value(200.00))
                .andExpect(jsonPath("$.data.deliveryCollectionAmount").value(200.00))
                .andExpect(jsonPath("$.data.razorpayAmount").value(20000));
    }

    @Test
    @DisplayName("POST /api/payments/online/verify - Returns 200 with captured status")
    void testVerifyPaymentMvc() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .paymentPublicId("pay-uuid-mvc-3")
                .paymentStatus(PaymentStatus.PAID)
                .totalAmount(new BigDecimal("400.00"))
                .advancePaidAmount(new BigDecimal("400.00"))
                .outstandingAmount(BigDecimal.ZERO)
                .transactions(new ArrayList<>())
                .build();

        when(onlinePaymentService.verifyPayment(any(PaymentVerificationRequest.class), any())).thenReturn(response);

        PaymentVerificationRequest req = new PaymentVerificationRequest("pay-uuid-3", "order_rzp", "pay_rzp", "sig_rzp");

        paymentMockMvc.perform(post("/api/payments/online/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"));
    }

    @Test
    @DisplayName("POST /api/internal/payments/{id}/cod/collect - Records COD collection")
    void testCollectCodInternalMvc() throws Exception {
        CodCollectionResponse response = CodCollectionResponse.builder()
                .collectionPublicId("col-uuid-1")
                .paymentPublicId("pay-uuid-4")
                .orderPublicId("order-400")
                .collectedAmount(new BigDecimal("200.00"))
                .remainingOutstandingAmount(BigDecimal.ZERO)
                .paymentStatus(PaymentStatus.PAID)
                .build();

        when(codCollectionService.collectCod(eq("pay-uuid-4"), any(CodCollectionRequest.class), any()))
                .thenReturn(response);

        CodCollectionRequest req = CodCollectionRequest.builder()
                .orderPublicId("order-400")
                .collectedAmount(new BigDecimal("200.00"))
                .deliveryReference("del-ref-1")
                .collectorReference("agent-1")
                .idempotencyKey("idemp-col-1")
                .build();

        internalMockMvc.perform(post("/api/internal/payments/pay-uuid-4/cod/collect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.collectionPublicId").value("col-uuid-1"))
                .andExpect(jsonPath("$.data.collectedAmount").value(200.00))
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"));
    }

    @Test
    @DisplayName("GET /api/admin/payments/statistics - Returns 200 with platform totals")
    void testAdminStatisticsMvc() throws Exception {
        PaymentStatisticsResponse response = PaymentStatisticsResponse.builder()
                .totalOrdersAmount(new BigDecimal("10000.00"))
                .totalPaymentsCount(25L)
                .fullOnlineCount(10L)
                .partialOnlineCodCount(15L)
                .totalCollectedAmount(new BigDecimal("7500.00"))
                .outstandingAmount(new BigDecimal("2500.00"))
                .paidCount(20L)
                .build();

        when(paymentService.getPaymentStatistics()).thenReturn(response);

        adminMockMvc.perform(get("/api/admin/payments/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalOrdersAmount").value(10000.00))
                .andExpect(jsonPath("$.data.totalPaymentsCount").value(25))
                .andExpect(jsonPath("$.data.partialOnlineCodCount").value(15));
    }

    @Test
    @DisplayName("POST /api/webhooks/razorpay - Returns 200 on valid signature")
    void testWebhookReceiverMvc() throws Exception {
        String payload = "{\"event\":\"payment.captured\"}";
        doNothing().when(webhookService).processRazorpayWebhook(anyString(), anyString());

        webhookMockMvc.perform(post("/api/webhooks/razorpay")
                        .header("X-Razorpay-Signature", "valid_header_signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
