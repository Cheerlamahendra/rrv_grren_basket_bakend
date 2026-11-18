package com.rrvgreenbasket.delivery.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateDeliveryZoneRequest {

    @NotBlank(message = "Zone name is required")
    @Size(max = 100, message = "Zone name cannot exceed 100 characters")
    @Schema(example = "Kurnool Zone 1")
    private String zoneName;

    @NotBlank(message = "Zone code is required")
    @Size(max = 50, message = "Zone code cannot exceed 50 characters")
    @Schema(example = "KUR-ZONE-01")
    private String zoneCode;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    @Schema(example = "Delivery coverage for central Kurnool localities")
    private String description;

    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City cannot exceed 100 characters")
    @Schema(example = "Kurnool")
    private String city;

    @NotBlank(message = "State is required")
    @Size(max = 100, message = "State cannot exceed 100 characters")
    @Schema(example = "Andhra Pradesh")
    private String state;

    @Pattern(regexp = "^[0-9]{6}$", message = "Pincode must be a 6-digit number")
    @Schema(example = "518001")
    private String pincode;

    @Schema(description = "Optional set of multiple 6-digit PIN codes serviced by this zone", example = "[\"518001\", \"518002\"]")
    private java.util.Set<@Pattern(regexp = "^[0-9]{6}$", message = "Each PIN code must be a 6-digit number") String> pincodes;

    @NotNull(message = "Delivery charge is required")
    @DecimalMin(value = "0.00", inclusive = true, message = "Delivery charge cannot be negative")
    @Digits(integer = 8, fraction = 2, message = "Delivery charge format invalid")
    @Schema(example = "30.00")
    private BigDecimal deliveryCharge;

    @NotNull(message = "Minimum order amount is required")
    @DecimalMin(value = "0.00", inclusive = true, message = "Minimum order amount cannot be negative")
    @Digits(integer = 8, fraction = 2, message = "Minimum order amount format invalid")
    @Schema(example = "199.00")
    private BigDecimal minimumOrderAmount;

    @DecimalMin(value = "0.00", inclusive = true, message = "Free delivery threshold cannot be negative")
    @Digits(integer = 8, fraction = 2, message = "Free delivery threshold format invalid")
    @Schema(example = "499.00")
    private BigDecimal freeDeliveryThreshold;

    @NotBlank(message = "Estimated delivery time is required")
    @Size(max = 50, message = "Estimated delivery time cannot exceed 50 characters")
    @Schema(example = "NEXT_DAY")
    @Builder.Default
    private String estimatedDeliveryTime = "NEXT_DAY";

    @Builder.Default
    private Boolean active = true;

    @NotNull(message = "Boundary GeoJSON is required")
    @Schema(description = "GeoJSON Polygon object representing delivery zone boundaries",
            example = "{\"type\": \"Polygon\", \"coordinates\": [[[78.0373, 15.8281], [78.0473, 15.8281], [78.0473, 15.8381], [78.0373, 15.8381], [78.0373, 15.8281]]]}")
    private JsonNode boundary;
}
