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

    @NotNull(message = "Order acceptance enabled flag is required")
    @Schema(example = "true")
    private Boolean orderAcceptanceEnabled;

    @NotNull(message = "Opening time is required")
    @JsonFormat(pattern = "HH:mm")
    @Schema(type = "string", pattern = "HH:mm", example = "08:00")
    private LocalTime openingTime;

    @NotNull(message = "Closing time is required")
    @JsonFormat(pattern = "HH:mm")
    @Schema(type = "string", pattern = "HH:mm", example = "21:00")
    private LocalTime closingTime;

    @NotNull(message = "Delivery type is required")
    @Schema(example = "NEXT_DAY")
    private DeliveryType deliveryType;

    @NotNull(message = "Delivery days offset is required")
    @Min(value = 0, message = "Offset cannot be negative")
    @Schema(example = "1")
    private Integer deliveryDaysOffset;

    @Schema(example = "Asia/Kolkata")
    private String timezone;
}
