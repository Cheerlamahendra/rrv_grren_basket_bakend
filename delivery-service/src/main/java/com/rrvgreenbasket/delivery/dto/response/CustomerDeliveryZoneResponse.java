package com.rrvgreenbasket.delivery.dto.response;

import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerDeliveryZoneResponse {

    private String publicId;
    private String zoneName;
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
}
