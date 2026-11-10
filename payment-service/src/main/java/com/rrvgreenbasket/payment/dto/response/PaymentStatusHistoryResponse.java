package com.rrvgreenbasket.payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentStatusHistoryResponse {

    private String previousStatus;
    private String newStatus;
    private String reason;
    private String changedBy;
    private Instant changedAt;
}
