package com.rrvgreenbasket.order.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.rrvgreenbasket.order.enums.DeliveryType;
import lombok.*;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderBusinessSettingsResponse {

    private String publicId;
    private boolean orderAcceptanceEnabled;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime openingTime;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime closingTime;

    private DeliveryType deliveryType;
    private int deliveryDaysOffset;
    private String timezone;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
