package com.rrvgreenbasket.product.dto.request;

import com.rrvgreenbasket.product.enums.ProductStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatchProductRequest {

    private String categoryId;

    @Size(min = 2, max = 150, message = "Product name must be between 2 and 150 characters")
    private String name;

    @Size(max = 150, message = "Slug cannot exceed 150 characters")
    private String slug;

    private String description;

    @Size(max = 300, message = "Short description cannot exceed 300 characters")
    private String shortDescription;

    @DecimalMin(value = "0.01", message = "Price per KG must be positive")
    @Digits(integer = 10, fraction = 2, message = "Price format must be up to 10 integer digits and 2 decimal places")
    private BigDecimal pricePerKg;

    @DecimalMin(value = "0.001", message = "Default quantity in KG must be positive")
    @Digits(integer = 7, fraction = 3, message = "Default quantity must be up to 7 integer digits and 3 decimal places")
    private BigDecimal defaultQuantityKg;

    private String quantityUnit;

    private String imageUrl;

    private ProductStatus status;

    private Boolean adminEnabled;

    private Boolean isFeatured;
}
