package com.rrvgreenbasket.order.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.rrvgreenbasket.order.enums.DeliveryType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateOrderBusinessSettingsRequest {

    @Schema(example = "true")
    private Boolean orderAcceptanceEnabled;

    @NotNull(message = "Opening time is required")
    @JsonFormat(pattern = "HH:mm")
    @Schema(type = "string", pattern = "HH:mm", example = "06:00")
    private LocalTime openingTime;

    @NotNull(message = "Closing time is required")
    @JsonFormat(pattern = "HH:mm")
    @Schema(type = "string", pattern = "HH:mm", example = "22:00")
    private LocalTime closingTime;

    @Schema(example = "NEXT_DAY")
    private DeliveryType deliveryType;

    @Min(value = 0, message = "Offset cannot be negative")
    @Schema(example = "1")
    private Integer deliveryDaysOffset;

    @Schema(example = "Asia/Kolkata")
    private String timezone;
}
