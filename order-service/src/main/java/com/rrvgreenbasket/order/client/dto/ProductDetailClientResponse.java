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
public class ProductDetailClientResponse {

    private String publicId;
    private String name;
    private BigDecimal pricePerKg;
    private BigDecimal defaultWeightIncrement;
    private String unitOfMeasure;
    private BigDecimal sellingPricePerUnit;
    private String productImageUrl;
    private String status;
    private Boolean isAvailableForSale;
    private Boolean isOutOfStock;
    private Boolean inStock;
}
