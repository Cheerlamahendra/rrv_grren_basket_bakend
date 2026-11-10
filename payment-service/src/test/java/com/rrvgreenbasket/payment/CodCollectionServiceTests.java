package com.rrvgreenbasket.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.payment.dto.request.CodCollectionRequest;
import com.rrvgreenbasket.payment.dto.response.CodCollectionResponse;
import com.rrvgreenbasket.payment.entity.CodCollection;
import com.rrvgreenbasket.payment.entity.Payment;
import com.rrvgreenbasket.payment.enums.PaymentMethod;
import com.rrvgreenbasket.payment.enums.PaymentStatus;
import com.rrvgreenbasket.payment.exception.CodCollectionException;
import com.rrvgreenbasket.payment.outbox.PaymentEventPublisher;
import com.rrvgreenbasket.payment.repository.CodCollectionRepository;
import com.rrvgreenbasket.payment.repository.PaymentRepository;
import com.rrvgreenbasket.payment.repository.PaymentStatusHistoryRepository;
import com.rrvgreenbasket.payment.service.IdempotencyService;
import com.rrvgreenbasket.payment.service.impl.CodCollectionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Tests — COD Delivery Cash Collection Service")
class CodCollectionServiceTests {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private CodCollectionRepository codCollectionRepository;

    @Mock
    private PaymentStatusHistoryRepository historyRepository;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private PaymentEventPublisher eventPublisher;

    private CodCollectionServiceImpl codCollectionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String paymentId = "pay-uuid-cod-1";
    private final String orderId = "order-uuid-cod-1";
    private final String idempotencyKey = "idemp-key-cod-100";

    @BeforeEach
    void setUp() {
        codCollectionService = new CodCollectionServiceImpl(
                paymentRepository,
                codCollectionRepository,
                historyRepository,
                idempotencyService,
                eventPublisher,
                objectMapper
        );
    }

    @Test
    @DisplayName("Scenario C: Successful Delivery Cash Collection of Remaining 50% Balance -> Becomes PAID")
    void testSuccessfulDeliveryCashCollection() {
        Payment payment = Payment.builder()
                .id(1L)
                .paymentPublicId(paymentId)
                .orderPublicId(orderId)
                .customerPublicId("cust-1")
                .paymentMethod(PaymentMethod.PARTIAL_ONLINE_COD)
                .paymentStatus(PaymentStatus.DELIVERY_COLLECTION_PENDING)
                .totalAmount(new BigDecimal("400.00"))
                .advancePercentage(50)
                .advanceAmount(new BigDecimal("200.00"))
                .advancePaidAmount(new BigDecimal("200.00"))
                .deliveryCollectionAmount(new BigDecimal("200.00"))
                .deliveryCollectedAmount(BigDecimal.ZERO)
                .outstandingAmount(new BigDecimal("200.00"))
                .currency("INR")
                .build();

        when(idempotencyService.checkAndAcquire(eq(idempotencyKey), eq("COD_COLLECTION"), eq(paymentId), anyString()))
                .thenReturn(new IdempotencyService.IdempotencyCheck(IdempotencyService.IdempotencyResult.NEW, null));
        when(paymentRepository.findByPaymentPublicId(paymentId)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        CodCollectionRequest request = CodCollectionRequest.builder()
                .orderPublicId(orderId)
                .collectedAmount(new BigDecimal("200.00"))
                .deliveryReference("del-ref-999")
                .collectorReference("agent-ramesh")
                .idempotencyKey(idempotencyKey)
                .build();

        CodCollectionResponse response = codCollectionService.collectCod(paymentId, request, "agent-ramesh");

        assertNotNull(response);
        assertEquals(new BigDecimal("200.00"), response.getCollectedAmount());
        assertEquals(new BigDecimal("0.00"), response.getRemainingOutstandingAmount());
        assertEquals(PaymentStatus.PAID, response.getPaymentStatus());

        verify(idempotencyService).complete(eq(idempotencyKey), anyString());
        verify(eventPublisher).publishEvent(any(Payment.class), eq("COD_PAYMENT_COLLECTED"), isNull(), isNull(), isNull());
        verify(eventPublisher).publishEvent(any(Payment.class), eq("PAYMENT_COMPLETED"), isNull(), isNull(), isNull());
    }

    @Test
    @DisplayName("Scenario E: Duplicate Cash Collection Request is Idempotent (Returns existing record)")
    void testDuplicateCashCollectionIdempotency() {
        when(idempotencyService.checkAndAcquire(eq(idempotencyKey), eq("COD_COLLECTION"), eq(paymentId), anyString()))
                .thenReturn(new IdempotencyService.IdempotencyCheck(IdempotencyService.IdempotencyResult.ALREADY_COMPLETED, "col-existing-1"));

        Payment payment = Payment.builder()
                .paymentPublicId(paymentId)
                .orderPublicId(orderId)
                .totalAmount(new BigDecimal("400.00"))
                .advancePaidAmount(new BigDecimal("200.00"))
                .deliveryCollectedAmount(new BigDecimal("200.00"))
                .outstandingAmount(BigDecimal.ZERO)
                .paymentStatus(PaymentStatus.PAID)
                .build();

        CodCollection existing = CodCollection.builder()
                .collectionPublicId("col-existing-1")
                .payment(payment)
                .orderPublicId(orderId)
                .collectedAmount(new BigDecimal("200.00"))
                .collectorReference("agent-ramesh")
                .deliveryReference("del-ref-999")
                .idempotencyKey(idempotencyKey)
                .collectedAt(Instant.now())
                .build();

        when(codCollectionRepository.findByCollectionPublicId("col-existing-1")).thenReturn(Optional.of(existing));

        CodCollectionRequest request = CodCollectionRequest.builder()
                .orderPublicId(orderId)
                .collectedAmount(new BigDecimal("200.00"))
                .deliveryReference("del-ref-999")
                .collectorReference("agent-ramesh")
                .idempotencyKey(idempotencyKey)
                .build();

        CodCollectionResponse response = codCollectionService.collectCod(paymentId, request, "agent-ramesh");

        assertNotNull(response);
        assertEquals("col-existing-1", response.getCollectionPublicId());
        // Verify payment balance was NOT reduced twice!
        verify(paymentRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Reject Cash Collection when Collection Exceeds Outstanding Balance")
    void testCollectionExceedingOutstandingBalanceRejected() {
        Payment payment = Payment.builder()
                .paymentPublicId(paymentId)
                .orderPublicId(orderId)
                .paymentMethod(PaymentMethod.PARTIAL_ONLINE_COD)
                .paymentStatus(PaymentStatus.DELIVERY_COLLECTION_PENDING)
                .totalAmount(new BigDecimal("400.00"))
                .advancePaidAmount(new BigDecimal("200.00"))
                .outstandingAmount(new BigDecimal("200.00"))
                .build();

        when(idempotencyService.checkAndAcquire(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new IdempotencyService.IdempotencyCheck(IdempotencyService.IdempotencyResult.NEW, null));
        when(paymentRepository.findByPaymentPublicId(paymentId)).thenReturn(Optional.of(payment));

        CodCollectionRequest request = CodCollectionRequest.builder()
                .orderPublicId(orderId)
                .collectedAmount(new BigDecimal("250.00")) // Exceeds ₹200.00 balance!
                .deliveryReference("del-ref")
                .collectorReference("agent")
                .idempotencyKey("idemp-exceed")
                .build();

        assertThrows(CodCollectionException.class, () -> codCollectionService.collectCod(paymentId, request, "agent"));
        verify(idempotencyService).fail("idemp-exceed");
    }

    @Test
    @DisplayName("Reject Cash Collection before Online Advance is Captured")
    void testCollectionBeforeAdvancePaymentRejected() {
        Payment payment = Payment.builder()
                .paymentPublicId(paymentId)
                .orderPublicId(orderId)
                .paymentMethod(PaymentMethod.PARTIAL_ONLINE_COD)
                .paymentStatus(PaymentStatus.ADVANCE_PAYMENT_INITIATED) // Not yet captured!
                .totalAmount(new BigDecimal("400.00"))
                .advanceAmount(new BigDecimal("200.00"))
                .advancePaidAmount(BigDecimal.ZERO)
                .outstandingAmount(new BigDecimal("400.00"))
                .build();

        when(idempotencyService.checkAndAcquire(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new IdempotencyService.IdempotencyCheck(IdempotencyService.IdempotencyResult.NEW, null));
        when(paymentRepository.findByPaymentPublicId(paymentId)).thenReturn(Optional.of(payment));

        CodCollectionRequest request = CodCollectionRequest.builder()
                .orderPublicId(orderId)
                .collectedAmount(new BigDecimal("200.00"))
                .deliveryReference("del-ref")
                .collectorReference("agent")
                .idempotencyKey("idemp-early")
                .build();

        assertThrows(CodCollectionException.class, () -> codCollectionService.collectCod(paymentId, request, "agent"));
    }
}
