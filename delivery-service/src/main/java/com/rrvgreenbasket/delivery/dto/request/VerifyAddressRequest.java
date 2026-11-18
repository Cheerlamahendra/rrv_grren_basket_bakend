package com.rrvgreenbasket.delivery.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerifyAddressRequest {

    @NotBlank(message = "Google Place ID is required")
    @Schema(description = "Google Place ID returned from Google Places Autocomplete", example = "ChIJ7eXy0rP_yDsRzZqj9Y1Lw6s")
    private String placeId;
}
