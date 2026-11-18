package com.rrvgreenbasket.delivery.dto.response;

import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryZoneResponse {

    private String publicId;
    private String zoneName;
    private String zoneCode;
    private String description;
    private String city;
    private String state;
    private String pincode;
    private java.util.Set<String> pincodes;

    @JsonRawValue
    private String boundary;

    private BigDecimal deliveryCharge;
    private BigDecimal minimumOrderAmount;
    private BigDecimal freeDeliveryThreshold;
    private String estimatedDeliveryTime;
    private boolean active;
    private boolean deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
