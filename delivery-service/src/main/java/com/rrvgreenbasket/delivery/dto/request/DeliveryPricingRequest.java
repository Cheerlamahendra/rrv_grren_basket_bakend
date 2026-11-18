package com.rrvgreenbasket.delivery.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryPricingRequest {

    @NotNull(message = "Minimum distance in meters is required")
    @Min(value = 0, message = "Minimum distance cannot be negative")
    @Schema(description = "Minimum road distance in meters (inclusive)", example = "0")
    private Integer minimumDistanceMeters;

    @NotNull(message = "Maximum distance in meters is required")
    @Min(value = 1, message = "Maximum distance must be at least 1 meter")
    @Schema(description = "Maximum road distance in meters (inclusive)", example = "1000")
    private Integer maximumDistanceMeters;

    @NotNull(message = "Delivery charge is required")
    @DecimalMin(value = "0.0", message = "Delivery charge cannot be negative")
    @Schema(description = "Delivery fee charged for orders within this distance range", example = "10.00")
    private BigDecimal deliveryCharge;

    @Schema(description = "ISO currency code", example = "INR")
    @Builder.Default
    private String currency = "INR";

    @Schema(description = "Active status of pricing slab", example = "true")
    @Builder.Default
    private Boolean active = true;
}
