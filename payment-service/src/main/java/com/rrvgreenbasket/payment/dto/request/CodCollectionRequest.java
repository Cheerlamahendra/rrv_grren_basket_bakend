package com.rrvgreenbasket.payment.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodCollectionRequest {

    @NotBlank(message = "Order public ID is required")
    private String orderPublicId;

    @NotNull(message = "Collected amount is required")
    @DecimalMin(value = "0.01", message = "Collected amount must be greater than zero")
    private BigDecimal collectedAmount;

    @NotBlank(message = "Delivery reference is required")
    private String deliveryReference;

    @NotBlank(message = "Collector reference is required")
    private String collectorReference;

    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;
}
