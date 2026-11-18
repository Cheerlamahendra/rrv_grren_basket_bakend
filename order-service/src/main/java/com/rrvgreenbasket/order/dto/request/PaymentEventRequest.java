package com.rrvgreenbasket.order.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentEventRequest {

    @NotBlank(message = "Event type is required")
    @Schema(example = "PAYMENT_SUCCESS")
    private String eventType;

    @Schema(example = "COMPLETED")
    private String status;

    @Schema(example = "pay_123456789")
    private String paymentId;

    private BigDecimal amount;
    private String failureReason;
}
