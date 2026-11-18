package com.rrvgreenbasket.delivery.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeliveryPricingResponse {

    @Schema(description = "Slab identifier", example = "1")
    private Long id;

    @Schema(description = "Minimum road distance in meters (inclusive)", example = "0")
    private Integer minimumDistanceMeters;

    @Schema(description = "Maximum road distance in meters (inclusive)", example = "1000")
    private Integer maximumDistanceMeters;

    @Schema(description = "Minimum road distance in kilometers", example = "0.0")
    private Double minimumDistanceKm;

    @Schema(description = "Maximum road distance in kilometers", example = "1.0")
    private Double maximumDistanceKm;

    @Schema(description = "Delivery charge in specified currency", example = "10.00")
    private BigDecimal deliveryCharge;

    @Schema(description = "ISO currency code", example = "INR")
    private String currency;

    @Schema(description = "Active status of pricing slab", example = "true")
    private Boolean active;
}
