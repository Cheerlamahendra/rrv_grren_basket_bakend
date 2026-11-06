package com.rrvgreenbasket.inventory.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockAdjustmentRequest {

    @NotBlank(message = "Product public ID is required")
    @Size(max = 36, message = "Product public ID cannot exceed 36 characters")
    private String productPublicId;

    private BigDecimal adjustmentQuantity;

    private BigDecimal newPhysicalQuantity;

    @Size(max = 20, message = "Unit of measure cannot exceed 20 characters")
    private String unitOfMeasure;

    @NotBlank(message = "Adjustment reason is required")
    @Size(max = 100, message = "Reason cannot exceed 100 characters")
    private String reason;

    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    private String notes;

    @Size(max = 50, message = "Reference type cannot exceed 50 characters")
    private String referenceType;

    @Size(max = 100, message = "Reference ID cannot exceed 100 characters")
    private String referenceId;
}
