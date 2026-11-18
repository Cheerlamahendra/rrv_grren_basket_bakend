package com.rrvgreenbasket.delivery.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomerDeliveryAddressResponse {

    @Schema(description = "Address public UUID", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private String publicId;

    @Schema(description = "Google Place ID", example = "ChIJ7eXy0rP_yDsRzZqj9Y1Lw6s")
    private String googlePlaceId;

    @Schema(description = "Full formatted address string", example = "Flat 302, Green Avenue, Kurnool, Andhra Pradesh 518001")
    private String formattedAddress;

    @Schema(description = "Address line details", example = "Flat 302, Green Avenue")
    private String addressLine;

    @Schema(description = "Landmark", example = "Opposite City Park")
    private String landmark;

    @Schema(example = "Kurnool")
    private String city;

    @Schema(example = "Andhra Pradesh")
    private String state;

    @Schema(example = "518001")
    private String postalCode;

    @Schema(example = "India")
    private String country;

    @Schema(example = "15.8281")
    private Double latitude;

    @Schema(example = "78.0373")
    private Double longitude;

    @Schema(description = "Whether this is the customer's default address", example = "true")
    private Boolean isDefault;

    @Schema(description = "Whether the address was verified with Google", example = "true")
    private Boolean verified;
}
