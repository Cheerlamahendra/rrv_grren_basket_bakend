package com.rrvgreenbasket.inventory.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
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
public class SingleProductInitialStockRequest {

    @NotBlank(message = "Unit of measure is required")
    @Size(max = 20, message = "Unit of measure cannot exceed 20 characters")
    private String unitOfMeasure;

    @NotNull(message = "Initial quantity is required")
    @DecimalMin(value = "0.000", inclusive = true, message = "Initial quantity cannot be negative")
    @JsonAlias({"initialPhysicalQuantity", "quantity"})
    @JsonProperty("initialQuantity")
    private BigDecimal initialQuantity;

    @DecimalMin(value = "0.000", inclusive = true, message = "Low stock threshold cannot be negative")
    private BigDecimal lowStockThreshold;

    @NotBlank(message = "Reason is required")
    @Size(max = 100, message = "Reason cannot exceed 100 characters")
    private String reason;

    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    private String notes;
}
