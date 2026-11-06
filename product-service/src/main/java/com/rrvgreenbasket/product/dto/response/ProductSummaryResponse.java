package com.rrvgreenbasket.product.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rrvgreenbasket.product.enums.StockStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductSummaryResponse {

    private String publicId;
    private String name;
    private String slug;
    private CategoryResponse category;
    private String description;
    private String shortDescription;
    private String imageUrl;
    private BigDecimal pricePerKg;
    private BigDecimal defaultQuantityKg;
    private String quantityUnit;
    private BigDecimal totalPrice;
    private Boolean available;
    private StockStatus stockStatus;
    private Boolean isFeatured;
    @Builder.Default
    private List<ProductImageResponse> images = new ArrayList<>();
}
