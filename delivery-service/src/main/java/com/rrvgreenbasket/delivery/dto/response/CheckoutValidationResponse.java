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
public class CheckoutValidationResponse {

    @Schema(description = "Whether checkout delivery validation passed", example = "true")
    private boolean valid;

    @Schema(description = "Whether delivery is available", example = "true")
    private boolean available;

    @Schema(description = "Whether destination coordinates/place were verified", example = "true")
    private boolean verified;

    @Schema(description = "Associated cart identifier", example = "cart-12345")
    private String cartId;

    @Schema(description = "Serving store identifier", example = "store-kurnool-primary")
    private String storeId;

    @Schema(description = "Serving store display name", example = "RRV Green Basket - Kurnool Central")
    private String storeName;

    @Schema(description = "Verified Google Place ID", example = "ChIJ7eXy0rP_yDsRzZqj9Y1Lw6s")
    private String placeId;

    @Schema(description = "Verified formatted delivery address", example = "Collector Office Road, Kurnool, Andhra Pradesh 518001")
    private String deliveryAddress;

    @Schema(description = "Verified destination latitude", example = "15.8281")
    private Double latitude;

    @Schema(description = "Verified destination longitude", example = "78.0373")
    private Double longitude;

    @Schema(description = "Actual driving distance in meters", example = "2350")
    private Integer distanceMeters;

    @Schema(description = "Actual driving distance in kilometers", example = "2.35")
    private Double distanceKm;

    @Schema(description = "Estimated driving duration", example = "600s")
    private String estimatedTravelDuration;

    @Schema(description = "Authoritative distance-based delivery charge", example = "20.00")
    private BigDecimal deliveryCharge;

    @Schema(description = "Currency", example = "INR")
    @Builder.Default
    private String currency = "INR";

    @Schema(description = "Minimum order amount requirement", example = "0.00")
    private BigDecimal minimumOrderAmount;

    @Schema(description = "Subtotal threshold for free delivery", example = "500.00")
    private BigDecimal freeDeliveryThreshold;

    @Schema(description = "Matched pricing slab ID", example = "3")
    private Long pricingSlabId;

    @Schema(description = "Validation status or error explanation", example = "Checkout delivery location and pricing validated successfully")
    private String message;
}
