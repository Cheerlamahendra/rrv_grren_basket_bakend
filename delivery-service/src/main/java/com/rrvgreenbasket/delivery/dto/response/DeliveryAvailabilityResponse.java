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
public class DeliveryAvailabilityResponse {

    @Schema(description = "Whether delivery is available to this destination", example = "true")
    private boolean available;

    @Schema(description = "Whether the address location was verified via Google Maps", example = "true")
    @Builder.Default
    private boolean verified = true;

    @Schema(description = "Actual driving distance in meters from primary store", example = "2350")
    private Integer distanceMeters;

    @Schema(description = "Actual driving distance in kilometers", example = "2.35")
    private Double distanceKm;

    @Schema(description = "Estimated driving duration from Google Routes API", example = "600s")
    private String estimatedTravelDuration;

    @Schema(description = "Distance-based delivery charge calculated from pricing slabs", example = "20.00")
    private BigDecimal deliveryCharge;

    @Schema(description = "Currency", example = "INR")
    @Builder.Default
    private String currency = "INR";

    @Schema(description = "Configured store maximum delivery distance in kilometers", example = "5.0")
    private Double maximumDeliveryDistanceKm;

    @Schema(description = "Informative status message", example = "Delivery is available at your location")
    private String message;

    // --- Backward Compatibility Fields for Order Service & Legacy Consumers ---

    @Schema(description = "Store / Zone identifier for backward compatibility", example = "store-kurnool-primary")
    private String zonePublicId;

    @Schema(description = "Store / Zone display name for backward compatibility", example = "RRV Green Basket - Kurnool Central")
    private String zoneName;

    @Schema(description = "Minimum cart amount required for order placement", example = "0.00")
    private BigDecimal minimumOrderAmount;

    @Schema(description = "Cart subtotal threshold qualifying for free delivery waiver", example = "500.00")
    private BigDecimal freeDeliveryThreshold;

    @Schema(description = "Estimated delivery timeframe", example = "30-45 mins")
    private String estimatedDeliveryTime;
}
