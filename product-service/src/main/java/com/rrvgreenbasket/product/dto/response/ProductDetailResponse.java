package com.rrvgreenbasket.product.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rrvgreenbasket.product.enums.ProductStatus;
import com.rrvgreenbasket.product.enums.StockStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductDetailResponse {

    private String publicId;
    private String name;
    private String slug;
    private CategoryResponse category;
    private String description;
    private String shortDescription;
    private BigDecimal pricePerKg;
    private BigDecimal defaultQuantityKg;
    private String quantityUnit;
    private BigDecimal totalPrice;
    private String imageUrl;
    private ProductStatus status;
    private Boolean adminEnabled;
    private Boolean isFeatured;
    private Boolean available;
    private StockStatus stockStatus;
    @Builder.Default
    private List<ProductImageResponse> images = new ArrayList<>();
    private Instant createdAt;
    private Instant updatedAt;
}
