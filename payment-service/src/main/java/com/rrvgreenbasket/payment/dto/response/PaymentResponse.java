package com.rrvgreenbasket.payment.dto.response;

import com.rrvgreenbasket.payment.enums.PaymentMethod;
import com.rrvgreenbasket.payment.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private String paymentPublicId;
    private String orderPublicId;
    private String customerPublicId;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
    private String currency;
    private BigDecimal totalAmount;
    private Integer advancePercentage;
    private BigDecimal advanceAmount;
    private BigDecimal advancePaidAmount;
    private BigDecimal deliveryCollectionAmount;
    private BigDecimal deliveryCollectedAmount;
    private BigDecimal outstandingAmount;
    private BigDecimal refundedAmount;
    private Instant createdAt;
    private Instant updatedAt;
    private List<PaymentTransactionResponse> transactions;
}
