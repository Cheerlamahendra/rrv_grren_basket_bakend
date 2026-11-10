package com.rrvgreenbasket.payment.dto.response;

import com.rrvgreenbasket.payment.enums.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnlinePaymentInitiateResponse {

    private String paymentPublicId;
    private String razorpayOrderId;
    private String razorpayKeyId;
    private Long amount; // in paise
    private String currency;
    private PaymentMethod paymentMethod;
}
