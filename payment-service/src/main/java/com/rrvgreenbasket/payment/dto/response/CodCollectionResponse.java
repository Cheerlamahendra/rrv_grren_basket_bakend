package com.rrvgreenbasket.payment.dto.response;

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
public class CodCollectionResponse {

    private String collectionPublicId;
    private String paymentPublicId;
    private String orderPublicId;
    private BigDecimal collectedAmount;
    private BigDecimal totalAmount;
    private BigDecimal advancePaidAmount;
    private BigDecimal totalDeliveryCollectedAmount;
    private BigDecimal remainingOutstandingAmount;
    private PaymentStatus paymentStatus;
    private String collectorReference;
    private String deliveryReference;
    private String idempotencyKey;
    private Instant collectedAt;
}
