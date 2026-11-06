package com.rrvgreenbasket.inventory.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateThresholdRequest {

    @Size(max = 36, message = "Product public ID cannot exceed 36 characters")
    private String productPublicId;

    @NotNull(message = "Low stock threshold is required")
    @DecimalMin(value = "0.000", inclusive = true, message = "Low stock threshold cannot be negative")
    private BigDecimal lowStockThreshold;
}
