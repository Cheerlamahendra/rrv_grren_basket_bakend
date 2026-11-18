package com.rrvgreenbasket.order.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderStatisticsResponse {

    private long totalOrders;
    private long todayOrders;
    private long pendingOrders;
    private long confirmedOrders;
    private long processingOrders;
    private long packedOrders;
    private long outForDeliveryOrders;
    private long deliveredOrders;
    private long cancelledOrders;
    private BigDecimal totalRevenue;
    private BigDecimal todayRevenue;
}
