package com.rrvgreenbasket.order.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeliveryAvailabilityClientResponse {

    private Boolean available;
    private String zonePublicId;
    private String zoneName;
    private BigDecimal deliveryCharge;
    private BigDecimal minimumOrderAmount;
    private BigDecimal freeDeliveryThreshold;
    private String estimatedDeliveryTime;
    private String message;
}
