package com.rrvgreenbasket.payment.dto.response;

import com.rrvgreenbasket.payment.enums.RefundStatus;
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
public class PaymentRefundResponse {

    private String refundPublicId;
    private String paymentPublicId;
    private String providerRefundId;
    private BigDecimal refundAmount;
    private RefundStatus refundStatus;
    private String refundReason;
    private String idempotencyKey;
    private Instant createdAt;
}
