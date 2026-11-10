package com.rrvgreenbasket.payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentStatisticsResponse {

    private BigDecimal totalOrdersAmount;
    private Long totalPaymentsCount;
    private Long fullOnlineCount;
    private Long partialOnlineCodCount;
    private BigDecimal advanceAmountPayable;
    private BigDecimal advanceAmountReceived;
    private BigDecimal deliveryCashPayable;
    private BigDecimal deliveryCashCollected;
    private BigDecimal outstandingAmount;
    private BigDecimal totalCollectedAmount;
    private BigDecimal totalRefundedAmount;
    private Long paidCount;
    private Long failedCount;
    private Long pendingCount;
    private Long partiallyPaidCount;
    private Long deliveryCollectionPendingCount;
}
