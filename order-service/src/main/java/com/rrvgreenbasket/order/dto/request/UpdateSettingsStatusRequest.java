package com.rrvgreenbasket.order.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateSettingsStatusRequest {

    @NotNull(message = "Order acceptance status is required")
    private Boolean orderAcceptanceEnabled;
}
