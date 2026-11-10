package com.rrvgreenbasket.payment.dto.response;

import com.rrvgreenbasket.payment.enums.PaymentMethod;
import com.rrvgreenbasket.payment.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartialCodPaymentInitiateResponse {

    private String paymentPublicId;
    private String orderPublicId;
    private PaymentMethod paymentMethod;
    private BigDecimal totalAmount;
    private Integer advancePercentage;
    private BigDecimal advanceAmount;
    private BigDecimal deliveryCollectionAmount;
    private BigDecimal advancePaidAmount;
    private BigDecimal deliveryCollectedAmount;
    private BigDecimal outstandingAmount;
    private String currency;
    private PaymentStatus paymentStatus;
    private String razorpayOrderId;
    private String razorpayKeyId;
    private Long razorpayAmount; // in paise
}
