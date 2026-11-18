package com.rrvgreenbasket.order.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressDto {

    @NotBlank(message = "Address line is required")
    @Schema(example = "House No. 10, Example Street")
    private String addressLine;

    @Schema(example = "Near Collector Office")
    private String landmark;

    @NotBlank(message = "City is required")
    @Schema(example = "Kurnool")
    private String city;

    @NotBlank(message = "State is required")
    @Schema(example = "Andhra Pradesh")
    private String state;

    @NotBlank(message = "Pincode is required")
    @Schema(example = "518001")
    private String pincode;

    @NotNull(message = "Delivery latitude is required")
    @DecimalMin(value = "-90.0", message = "Latitude must be >= -90.0")
    @DecimalMax(value = "90.0", message = "Latitude must be <= 90.0")
    @Schema(example = "15.8281")
    private BigDecimal latitude;

    @NotNull(message = "Delivery longitude is required")
    @DecimalMin(value = "-180.0", message = "Longitude must be >= -180.0")
    @DecimalMax(value = "180.0", message = "Longitude must be <= 180.0")
    @Schema(example = "78.0373")
    private BigDecimal longitude;
}
