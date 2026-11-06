package com.rrvgreenbasket.customer.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rrvgreenbasket.customer.enums.AddressType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AddressResponse
 *
 * <p><strong>What it does:</strong></p>
 * Outbound DTO representing a customer's delivery address.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressResponse {

    private String id;
    private String customerId;
    private AddressType addressType;
    private String receiverName;
    private String receiverMobile;
    private String addressLine1;
    private String addressLine2;
    private String landmark;
    private String city;
    private String state;
    private String pincode;
    private String country;
    @JsonProperty("isDefault")
    private boolean isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
