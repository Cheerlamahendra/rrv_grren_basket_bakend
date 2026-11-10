package com.rrvgreenbasket.wishlist.dto.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductDto {

    private String publicId;
    private String name;
    private String slug;
    private String description;
    private String shortDescription;
    private BigDecimal pricePerKg;
    private BigDecimal defaultQuantityKg;
    private String quantityUnit;
    private BigDecimal totalPrice;
    private String imageUrl;
    private String status;
    private Boolean adminEnabled;
    private Boolean isFeatured;
    private Boolean available;
    private String stockStatus;

    public boolean isActiveAndAvailable() {
        boolean activeStatus = "ACTIVE".equalsIgnoreCase(this.status);
        boolean enabled = adminEnabled == null || adminEnabled;
        return activeStatus && enabled;
    }
}
