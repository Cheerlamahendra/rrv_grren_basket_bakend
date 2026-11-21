package com.rrvgreenbasket.order.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rrvgreenbasket.order.enums.OrderStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderStatusHistoryResponse {

    private Long id;
    private String publicId;
    private OrderStatus previousStatus;
    private OrderStatus newStatus;
    private String changedBy;
    private String reason;
    private LocalDateTime timestamp;
}
