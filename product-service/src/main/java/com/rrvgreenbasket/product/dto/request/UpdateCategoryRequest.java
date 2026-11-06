package com.rrvgreenbasket.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCategoryRequest {

    @NotBlank(message = "Category name is required")
    @Size(min = 2, max = 100, message = "Category name must be between 2 and 100 characters")
    private String name;

    @Size(max = 100, message = "Slug cannot exceed 100 characters")
    private String slug;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    private String imageUrl;

    @NotNull(message = "Sort order is required")
    private Integer sortOrder;

    @NotNull(message = "isActive flag is required")
    private Boolean isActive;
}
