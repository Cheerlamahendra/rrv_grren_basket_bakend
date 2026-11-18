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
public class UpdateDeliveryZoneRequest {

    @NotBlank(message = "Zone name is required")
    @Size(max = 100, message = "Zone name cannot exceed 100 characters")
    private String zoneName;

    @Size(max = 50, message = "Zone code cannot exceed 50 characters")
    private String zoneCode;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City cannot exceed 100 characters")
    private String city;

    @NotBlank(message = "State is required")
    @Size(max = 100, message = "State cannot exceed 100 characters")
    private String state;

    @Pattern(regexp = "^$|^[0-9]{6}$", message = "Pincode must be empty or a 6-digit number")
    private String pincode;

    @Schema(description = "Optional set of multiple 6-digit PIN codes serviced by this zone")
    private java.util.Set<@Pattern(regexp = "^[0-9]{6}$", message = "Each PIN code must be a 6-digit number") String> pincodes;

    @Schema(description = "Explicit flag to clear all PIN code restrictions from this zone")
    private Boolean clearPincode;

    @NotNull(message = "Delivery charge is required")
    @DecimalMin(value = "0.00", inclusive = true, message = "Delivery charge cannot be negative")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal deliveryCharge;

    @NotNull(message = "Minimum order amount is required")
    @DecimalMin(value = "0.00", inclusive = true, message = "Minimum order amount cannot be negative")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal minimumOrderAmount;

    @DecimalMin(value = "0.00", inclusive = true, message = "Free delivery threshold cannot be negative")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal freeDeliveryThreshold;

    @NotBlank(message = "Estimated delivery time is required")
    @Size(max = 50, message = "Estimated delivery time cannot exceed 50 characters")
    private String estimatedDeliveryTime;

    private Boolean active;

    @Schema(description = "GeoJSON Polygon object. If provided, boundary will be updated.")
    private JsonNode boundary;
}
