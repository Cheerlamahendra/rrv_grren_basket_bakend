package com.rrvgreenbasket.payment.dto.request;

import com.rrvgreenbasket.payment.enums.PaymentMethod;
import com.rrvgreenbasket.payment.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentFilterRequest {

    private PaymentStatus status;
    private PaymentMethod method;
    private String orderPublicId;
    private String customerPublicId;
    private String providerOrderId;
    private String providerPaymentId;
    private Instant startDate;
    private Instant endDate;
}
