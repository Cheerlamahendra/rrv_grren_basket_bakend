package com.rrvgreenbasket.inventory.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CorrectInitialStockRequest {

    @NotBlank(message = "Product public ID is required")
    @Size(max = 36, message = "Product public ID cannot exceed 36 characters")
    private String productPublicId;

    @NotNull(message = "Corrected physical quantity is required")
    @DecimalMin(value = "0.000", inclusive = true, message = "Corrected quantity cannot be negative")
    private BigDecimal correctedQuantity;

    @NotBlank(message = "Correction reason is required")
    @Size(max = 100, message = "Reason cannot exceed 100 characters")
    private String reason;

    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    private String notes;
}
