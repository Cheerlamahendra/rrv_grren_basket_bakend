package com.rrvgreenbasket.delivery.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerDeliveryAddressRequest {

    @NotBlank(message = "Google Place ID is required")
    @Schema(description = "Verified Google Place ID", example = "ChIJ7eXy0rP_yDsRzZqj9Y1Lw6s")
    private String googlePlaceId;

    @NotBlank(message = "Formatted address is required")
    @Schema(description = "Full formatted address string from Google Places", example = "Flat 302, Green Avenue, Kurnool, Andhra Pradesh 518001")
    private String formattedAddress;

    @Schema(description = "Specific flat / house / street number", example = "Flat 302, Green Avenue")
    private String addressLine;

    @Schema(description = "Nearby landmark", example = "Opposite City Park")
    private String landmark;

    @NotBlank(message = "City is required")
    @Schema(example = "Kurnool")
    private String city;

    @NotBlank(message = "State is required")
    @Schema(example = "Andhra Pradesh")
    private String state;

    @Schema(example = "518001")
    private String postalCode;

    @Schema(example = "India")
    @Builder.Default
    private String country = "India";

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0", message = "Latitude must be >= -90.0")
    @DecimalMax(value = "90.0", message = "Latitude must be <= 90.0")
    @Schema(example = "15.8281")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0", message = "Longitude must be >= -180.0")
    @DecimalMax(value = "180.0", message = "Longitude must be <= 180.0")
    @Schema(example = "78.0373")
    private Double longitude;

    @Schema(description = "Mark this address as the default delivery destination", example = "true")
    @Builder.Default
    private Boolean isDefault = false;
}
