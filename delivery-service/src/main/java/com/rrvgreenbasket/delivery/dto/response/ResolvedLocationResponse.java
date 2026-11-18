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
public class ResolvedLocationResponse {

    @Schema(description = "Verification status from reverse geocoding", example = "true")
    private boolean verified;

    @Schema(description = "Google Place ID if resolved", example = "ChIJ7eXy0rP_yDsRzZqj9Y1Lw6s")
    private String placeId;

    @Schema(description = "Normalized address string", example = "Collector Office Road, Kurnool, Andhra Pradesh 518001, India")
    private String formattedAddress;

    @Schema(description = "Customer latitude", example = "15.8281")
    private Double latitude;

    @Schema(description = "Customer longitude", example = "78.0373")
    private Double longitude;

    @Schema(description = "Locality / City", example = "Kurnool")
    private String city;

    @Schema(description = "Administrative Area / State", example = "Andhra Pradesh")
    private String state;

    @Schema(description = "Postal PIN code", example = "518001")
    private String postalCode;

    @Schema(description = "Country", example = "India")
    private String country;

    @Schema(description = "Whether delivery is available at this resolved location", example = "true")
    private Boolean availableForDelivery;

    @Schema(description = "Actual driving distance in meters from primary store", example = "2350")
    private Integer distanceMeters;

    @Schema(description = "Actual driving distance in kilometers", example = "2.35")
    private Double distanceKm;

    @Schema(description = "Estimated driving duration", example = "10 mins")
    private String estimatedTravelDuration;

    @Schema(description = "Delivery fee for this location", example = "20.00")
    private BigDecimal deliveryCharge;

    @Schema(description = "Currency", example = "INR")
    private String currency;

    @Schema(description = "Informative status message", example = "Location resolved and delivery is available")
    private String message;
}
