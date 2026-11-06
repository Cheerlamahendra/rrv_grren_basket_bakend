package com.rrvgreenbasket.product.service;

import com.rrvgreenbasket.product.dto.request.CreateCategoryRequest;
import com.rrvgreenbasket.product.dto.request.UpdateCategoryRequest;
import com.rrvgreenbasket.product.dto.response.CategoryResponse;

import java.util.List;

public interface CategoryService {

    List<CategoryResponse> getActiveCategories();

    List<CategoryResponse> getAllCategoriesForAdmin();

    CategoryResponse getCategoryByPublicId(String publicId);

    CategoryResponse getCategoryBySlug(String slug);

    CategoryResponse createCategory(CreateCategoryRequest request);

    CategoryResponse updateCategory(String publicId, UpdateCategoryRequest request);

    CategoryResponse updateCategoryStatus(String publicId, boolean isActive);
}
