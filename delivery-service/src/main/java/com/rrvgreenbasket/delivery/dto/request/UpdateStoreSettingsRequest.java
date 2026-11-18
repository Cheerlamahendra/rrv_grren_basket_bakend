package com.rrvgreenbasket.delivery.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateStoreSettingsRequest {

    @NotBlank(message = "Store name is required")
    @Size(max = 150, message = "Store name cannot exceed 150 characters")
    @Schema(example = "RRV Green Basket - Kurnool Central")
    private String storeName;

    @NotBlank(message = "Store formatted address is required")
    @Size(max = 500, message = "Formatted address cannot exceed 500 characters")
    @Schema(example = "Near Collector Office, Kurnool, Andhra Pradesh 518001, India")
    private String formattedAddress;

    @Schema(description = "Google Place ID for verified store location", example = "ChIJ7eXy0rP_yDsRzZqj9Y1Lw6s")
    private String googlePlaceId;

    @NotNull(message = "Store latitude is required")
    @DecimalMin(value = "-90.0", message = "Latitude must be >= -90.0")
    @DecimalMax(value = "90.0", message = "Latitude must be <= 90.0")
    @Schema(example = "15.8281")
    private Double latitude;

    @NotNull(message = "Store longitude is required")
    @DecimalMin(value = "-180.0", message = "Longitude must be >= -180.0")
    @DecimalMax(value = "180.0", message = "Longitude must be <= 180.0")
    @Schema(example = "78.0373")
    private Double longitude;

    @NotBlank(message = "City is required")
    @Schema(example = "Kurnool")
    private String city;

    @NotBlank(message = "State is required")
    @Schema(example = "Andhra Pradesh")
    private String state;

    @NotBlank(message = "Postal PIN code is required")
    @Schema(example = "518001")
    private String postalCode;

    @Schema(example = "India")
    @Builder.Default
    private String country = "India";

    @NotNull(message = "Maximum delivery distance in meters is required")
    @Min(value = 100, message = "Maximum delivery distance must be at least 100 meters")
    @Max(value = 100000, message = "Maximum delivery distance cannot exceed 100,000 meters")
    @Schema(description = "Maximum road delivery distance in meters", example = "5000")
    @Builder.Default
    private Integer maximumDeliveryDistanceMeters = 5000;

    @NotNull(message = "Delivery enabled flag is required")
    @Schema(example = "true")
    @Builder.Default
    private Boolean deliveryEnabled = true;

    @DecimalMin(value = "0.0", message = "Minimum order amount cannot be negative")
    @Schema(example = "0.00")
    @Builder.Default
    private BigDecimal minimumOrderAmount = BigDecimal.ZERO;

    @DecimalMin(value = "0.0", message = "Free delivery threshold cannot be negative")
    @Schema(example = "500.00")
    private BigDecimal freeDeliveryThreshold;

    @Schema(example = "30-45 mins")
    @Builder.Default
    private String estimatedDeliveryTime = "30-45 mins";

    @Schema(example = "INR")
    @Builder.Default
    private String currency = "INR";
}
