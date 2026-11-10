package com.rrvgreenbasket.payment.dto.event;

import com.rrvgreenbasket.payment.enums.PaymentMethod;
import com.rrvgreenbasket.payment.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEventPayload {

    private String eventId;
    private String eventType;
    private String paymentPublicId;
    private String orderPublicId;
    private BigDecimal totalAmount;
    private BigDecimal advanceAmount;
    private BigDecimal advancePaidAmount;
    private BigDecimal deliveryCollectionAmount;
    private BigDecimal deliveryCollectedAmount;
    private BigDecimal outstandingAmount;
    private String currency;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
    private String providerPaymentId;
    private String providerOrderId;
    private String failureReason;
    @Builder.Default
    private Instant occurredAt = Instant.now();
}
