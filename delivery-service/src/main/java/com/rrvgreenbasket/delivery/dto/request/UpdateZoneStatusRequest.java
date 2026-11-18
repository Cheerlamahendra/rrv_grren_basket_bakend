package com.rrvgreenbasket.delivery.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateZoneStatusRequest {

    @NotNull(message = "Active status is required")
    private Boolean active;
}
