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
public class ReservationItemDto {

    @NotBlank(message = "Product public ID is required")
    @Size(max = 36, message = "Product public ID cannot exceed 36 characters")
    private String productPublicId;

    @NotNull(message = "Reservation quantity is required")
    @DecimalMin(value = "0.001", inclusive = true, message = "Reservation quantity must be greater than 0")
    private BigDecimal quantity;

    @Size(max = 20, message = "Unit of measure cannot exceed 20 characters")
    private String unitOfMeasure;
}
