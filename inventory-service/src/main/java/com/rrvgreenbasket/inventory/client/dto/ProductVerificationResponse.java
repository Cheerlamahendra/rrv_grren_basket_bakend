package com.rrvgreenbasket.inventory.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductVerificationResponse {

    private String publicId;
    private String name;
    private String quantityUnit;
    private String status;
    private Boolean available;
}
