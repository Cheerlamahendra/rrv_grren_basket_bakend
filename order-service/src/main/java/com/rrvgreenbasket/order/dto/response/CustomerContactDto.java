package com.rrvgreenbasket.order.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomerContactDto {

    @Schema(description = "Registered customer name from account profile", example = "Mahendra")
    private String registeredName;

    @Schema(description = "Registered customer mobile number from account profile", example = "8247329041")
    private String registeredMobile;

    @Schema(description = "Registered customer public ID", example = "726bfd9d-46b0-4822-bb21-a430c0f07a24")
    private String customerPublicId;
}
