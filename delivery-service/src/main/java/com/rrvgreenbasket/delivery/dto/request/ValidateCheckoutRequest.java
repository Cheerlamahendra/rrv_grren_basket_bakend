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
public class ValidateCheckoutRequest {

    @Schema(description = "Google Place ID of selected delivery address", example = "ChIJ7eXy0rP_yDsRzZqj9Y1Lw6s")
    private String placeId;

    @Schema(description = "Customer shopping cart ID to validate cart amount against delivery thresholds", example = "cart-12345")
    private String cartId;

    @DecimalMin(value = "-90.0", message = "Latitude must be >= -90.0")
    @DecimalMax(value = "90.0", message = "Latitude must be <= 90.0")
    @Schema(description = "Customer delivery latitude (fallback if placeId not used)", example = "15.8281")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "Longitude must be >= -180.0")
    @DecimalMax(value = "180.0", message = "Longitude must be <= 180.0")
    @Schema(description = "Customer delivery longitude (fallback if placeId not used)", example = "78.0373")
    private Double longitude;

    @Schema(description = "Delivery address line or full text snapshot", example = "Flat 302, Green Avenue, Kurnool")
    private String address;

    public boolean hasPlaceId() {
        return placeId != null && !placeId.isBlank();
    }

    public boolean hasCoordinates() {
        return latitude != null && longitude != null
                && !latitude.isNaN() && !longitude.isNaN()
                && !latitude.isInfinite() && !longitude.isInfinite();
    }
}
