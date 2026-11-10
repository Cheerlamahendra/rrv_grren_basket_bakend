package com.rrvgreenbasket.payment.dto.response;

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
public class PaymentHistoryResponse {

    private String paymentPublicId;
    private String orderPublicId;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
    private BigDecimal totalAmount;
    private BigDecimal advancePaidAmount;
    private BigDecimal deliveryCollectedAmount;
    private BigDecimal outstandingAmount;
    private BigDecimal refundedAmount;
    private String currency;
    private Instant createdAt;
}
