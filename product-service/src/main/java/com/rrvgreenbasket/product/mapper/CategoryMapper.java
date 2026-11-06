package com.rrvgreenbasket.product.mapper;

import com.rrvgreenbasket.product.dto.response.CategoryResponse;
import com.rrvgreenbasket.product.entity.ProductCategory;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public CategoryResponse toResponse(ProductCategory category) {
        if (category == null) {
            return null;
        }
        return CategoryResponse.builder()
                .publicId(category.getPublicId())
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .imageUrl(category.getImageUrl())
                .sortOrder(category.getSortOrder())
                .isActive(category.getIsActive())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}
