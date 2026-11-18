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
public class StoreLocationResponse {

    @Schema(description = "Store unique identifier", example = "store-kurnool-primary")
    private String storeId;

    @Schema(description = "Store name", example = "RRV Green Basket - Kurnool Central")
    private String storeName;

    @Schema(description = "Google Place ID of the store", example = "ChIJ7eXy0rP_yDsRzZqj9Y1Lw6s")
    private String googlePlaceId;

    @Schema(description = "Formatted physical store address", example = "Near Collector Office, Kurnool, Andhra Pradesh 518001, India")
    private String formattedAddress;

    @Schema(description = "Store latitude", example = "15.8281")
    private Double latitude;

    @Schema(description = "Store longitude", example = "78.0373")
    private Double longitude;

    @Schema(example = "Kurnool")
    private String city;

    @Schema(example = "Andhra Pradesh")
    private String state;

    @Schema(example = "518001")
    private String postalCode;

    @Schema(example = "India")
    private String country;

    @Schema(description = "Maximum delivery road radius in kilometers", example = "5.0")
    private Double maximumDeliveryDistanceKm;

    @Schema(description = "Maximum delivery road distance in meters", example = "5000")
    private Integer maximumDeliveryDistanceMeters;

    @Schema(description = "Whether delivery is active for this store", example = "true")
    private Boolean deliveryEnabled;

    @Schema(description = "Whether this is the primary operating store", example = "true")
    private Boolean primaryStore;

    @Schema(example = "INR")
    private String currency;

    @Schema(description = "Minimum cart amount required for delivery orders", example = "0.00")
    private BigDecimal minimumOrderAmount;

    @Schema(description = "Subtotal threshold for free delivery waiver", example = "500.00")
    private BigDecimal freeDeliveryThreshold;

    @Schema(example = "30-45 mins")
    private String estimatedDeliveryTime;
}
