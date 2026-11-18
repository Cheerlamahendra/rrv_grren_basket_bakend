package com.rrvgreenbasket.delivery.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckDeliveryAvailabilityRequest {

    @Schema(description = "Google Place ID for verified address resolution", example = "ChIJ7eXy0rP_yDsRzZqj9Y1Lw6s")
    private String placeId;

    @DecimalMin(value = "-90.0", message = "Latitude must be >= -90.0")
    @DecimalMax(value = "90.0", message = "Latitude must be <= 90.0")
    @Schema(description = "Customer delivery latitude (used if placeId not provided)", example = "15.8281")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "Longitude must be >= -180.0")
    @DecimalMax(value = "180.0", message = "Longitude must be <= 180.0")
    @Schema(description = "Customer delivery longitude (used if placeId not provided)", example = "78.0373")
    private Double longitude;

    @Schema(description = "Customer textual address", example = "Kurnool, Andhra Pradesh, India")
    private String address;

    @Schema(description = "Customer city", example = "Kurnool")
    private String city;

    @Schema(description = "Customer state", example = "Andhra Pradesh")
    private String state;

    @Schema(description = "Customer 6-digit postal PIN code", example = "518001")
    private String pincode;

    public boolean hasPlaceId() {
        return placeId != null && !placeId.isBlank();
    }

    public boolean hasCoordinates() {
        return latitude != null && longitude != null
                && !latitude.isNaN() && !longitude.isNaN()
                && !latitude.isInfinite() && !longitude.isInfinite();
    }
}
