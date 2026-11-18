package com.rrvgreenbasket.order.dto.response;

import com.rrvgreenbasket.order.enums.OrderStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderStatusHistoryResponse {

    private OrderStatus previousStatus;
    private OrderStatus newStatus;
    private String changedBy;
    private String reason;
    private LocalDateTime timestamp;
}
