package com.rrvgreenbasket.product.service.impl;

import com.rrvgreenbasket.product.dto.request.CreateCategoryRequest;
import com.rrvgreenbasket.product.dto.request.UpdateCategoryRequest;
import com.rrvgreenbasket.product.dto.response.CategoryResponse;
import com.rrvgreenbasket.product.entity.ProductCategory;
import com.rrvgreenbasket.product.exception.DuplicateResourceException;
import com.rrvgreenbasket.product.exception.ResourceNotFoundException;
import com.rrvgreenbasket.product.mapper.CategoryMapper;
import com.rrvgreenbasket.product.repository.ProductCategoryRepository;
import com.rrvgreenbasket.product.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final ProductCategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getActiveCategories() {
        return categoryRepository.findAllByIsActiveTrueOrderBySortOrderAscNameAsc()
                .stream()
                .map(categoryMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategoriesForAdmin() {
        return categoryRepository.findAllByOrderBySortOrderAscNameAsc()
                .stream()
                .map(categoryMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryByPublicId(String publicId) {
        ProductCategory category = categoryRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with publicId: " + publicId));
        return categoryMapper.toResponse(category);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryBySlug(String slug) {
        ProductCategory category = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with slug: " + slug));
        return categoryMapper.toResponse(category);
    }

    @Override
    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        String slug = (request.getSlug() != null && !request.getSlug().isBlank())
                ? slugify(request.getSlug())
                : slugify(request.getName());

        if (categoryRepository.existsByName(request.getName())) {
            throw new DuplicateResourceException("Category name already exists: " + request.getName());
        }
        if (categoryRepository.existsBySlug(slug)) {
            throw new DuplicateResourceException("Category slug already exists: " + slug);
        }

        ProductCategory category = ProductCategory.builder()
                .publicId(UUID.randomUUID().toString())
                .name(request.getName().trim())
                .slug(slug)
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        ProductCategory saved = categoryRepository.save(category);
        log.info("Created new category: publicId={}, name={}, slug={}", saved.getPublicId(), saved.getName(), saved.getSlug());
        return categoryMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(String publicId, UpdateCategoryRequest request) {
        ProductCategory category = categoryRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with publicId: " + publicId));

        String slug = (request.getSlug() != null && !request.getSlug().isBlank())
                ? slugify(request.getSlug())
                : slugify(request.getName());

        if (categoryRepository.existsByNameAndIdNot(request.getName(), category.getId())) {
            throw new DuplicateResourceException("Category name already exists: " + request.getName());
        }
        if (categoryRepository.existsBySlugAndIdNot(slug, category.getId())) {
            throw new DuplicateResourceException("Category slug already exists: " + slug);
        }

        category.setName(request.getName().trim());
        category.setSlug(slug);
        category.setDescription(request.getDescription());
        category.setImageUrl(request.getImageUrl());
        category.setSortOrder(request.getSortOrder());
        category.setIsActive(request.getIsActive());

        ProductCategory updated = categoryRepository.save(category);
        log.info("Updated category: publicId={}, name={}", updated.getPublicId(), updated.getName());
        return categoryMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public CategoryResponse updateCategoryStatus(String publicId, boolean isActive) {
        ProductCategory category = categoryRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with publicId: " + publicId));

        category.setIsActive(isActive);
        ProductCategory updated = categoryRepository.save(category);
        log.info("Updated category status: publicId={}, isActive={}", updated.getPublicId(), isActive);
        return categoryMapper.toResponse(updated);
    }

    private String slugify(String input) {
        if (input == null) {
            return "";
        }
        return input.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");
    }
}
