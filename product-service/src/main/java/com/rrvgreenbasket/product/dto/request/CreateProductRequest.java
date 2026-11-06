package com.rrvgreenbasket.product.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rrvgreenbasket.product.enums.ProductStatus;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductRequest {

    private String categoryId;

    private String categoryPublicId;

    @NotBlank(message = "Product name is required")
    @Size(min = 2, max = 150, message = "Product name must be between 2 and 150 characters")
    private String name;

    @Size(max = 150, message = "Slug cannot exceed 150 characters")
    private String slug;

    private String description;

    @Size(max = 300, message = "Short description cannot exceed 300 characters")
    private String shortDescription;

    @NotNull(message = "Price per KG is required")
    @DecimalMin(value = "0.01", message = "Price per KG must be positive")
    @Digits(integer = 10, fraction = 2, message = "Price format must be up to 10 integer digits and 2 decimal places")
    private BigDecimal pricePerKg;

    @DecimalMin(value = "0.001", message = "Default quantity in KG must be positive")
    @Digits(integer = 7, fraction = 3, message = "Default quantity must be up to 7 integer digits and 3 decimal places")
    @Builder.Default
    private BigDecimal defaultQuantityKg = new BigDecimal("1.000");

    @NotBlank(message = "Quantity unit is required")
    private String quantityUnit;

    private String imageUrl;

    @Builder.Default
    private ProductStatus status = ProductStatus.DRAFT;

    @Builder.Default
    private Boolean adminEnabled = true;

    @Builder.Default
    private Boolean isFeatured = false;

    public String getEffectiveCategoryId() {
        if (categoryPublicId != null && !categoryPublicId.isBlank()) {
            return categoryPublicId.trim();
        }
        return (categoryId != null && !categoryId.isBlank()) ? categoryId.trim() : null;
    }

    @AssertTrue(message = "Category identifier (categoryPublicId or categoryId) is required")
    public boolean isCategoryProvided() {
        return (categoryPublicId != null && !categoryPublicId.isBlank())
                || (categoryId != null && !categoryId.isBlank());
    }
}
