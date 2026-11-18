package com.rrvgreenbasket.delivery.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VerifiedAddressResponse {

    @Schema(description = "Whether the address was successfully verified by Google Places", example = "true")
    private boolean verified;

    @Schema(description = "Google Place ID", example = "ChIJ7eXy0rP_yDsRzZqj9Y1Lw6s")
    private String placeId;

    @Schema(description = "Official formatted address string", example = "Collector Office Road, Kurnool, Andhra Pradesh 518001, India")
    private String formattedAddress;

    @Schema(description = "Verified location latitude", example = "15.8281")
    private Double latitude;

    @Schema(description = "Verified location longitude", example = "78.0373")
    private Double longitude;

    @Schema(description = "City / locality", example = "Kurnool")
    private String city;

    @Schema(description = "State / administrative area", example = "Andhra Pradesh")
    private String state;

    @Schema(description = "Postal PIN code", example = "518001")
    private String postalCode;

    @Schema(description = "Country name", example = "India")
    private String country;

    @Schema(description = "ISO 2-letter country code", example = "IN")
    private String countryCode;

    @Schema(description = "Google Place Types", example = "[\"premise\", \"street_address\"]")
    private List<String> placeTypes;
}
