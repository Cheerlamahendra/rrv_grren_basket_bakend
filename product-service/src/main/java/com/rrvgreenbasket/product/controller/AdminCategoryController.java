package com.rrvgreenbasket.product.controller;

import com.rrvgreenbasket.product.dto.request.CreateCategoryRequest;
import com.rrvgreenbasket.product.dto.request.UpdateCategoryRequest;
import com.rrvgreenbasket.product.dto.response.ApiResponse;
import com.rrvgreenbasket.product.dto.response.CategoryResponse;
import com.rrvgreenbasket.product.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/admin/products/categories")
@RequiredArgsConstructor
@Tag(name = "Admin Category Management", description = "Administrative endpoints for managing product categories and their status")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPER_ADMIN', 'ADMIN')")
public class AdminCategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "List all categories (including inactive)")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getAllCategories() {
        List<CategoryResponse> categories = categoryService.getAllCategoriesForAdmin();
        return ResponseEntity.ok(ApiResponse.success("Categories retrieved successfully", categories));
    }

    @Operation(summary = "Get category by publicId")
    @GetMapping(value = "/{publicId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategoryById(@PathVariable String publicId) {
        CategoryResponse category = categoryService.getCategoryByPublicId(publicId);
        return ResponseEntity.ok(ApiResponse.success("Category retrieved successfully", category));
    }

    @Operation(summary = "Create a new category")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @Valid @RequestBody CreateCategoryRequest request) {
        log.info("Admin creating category: name={}", request.getName());
        CategoryResponse response = categoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Category created successfully", response));
    }

    @Operation(summary = "Update category information")
    @PutMapping(value = "/{publicId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @PathVariable String publicId,
            @Valid @RequestBody UpdateCategoryRequest request) {
        log.info("Admin updating category: publicId={}", publicId);
        CategoryResponse response = categoryService.updateCategory(publicId, request);
        return ResponseEntity.ok(ApiResponse.success("Category updated successfully", response));
    }

    @Operation(summary = "Activate or deactivate category")
    @PatchMapping(value = "/{publicId}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategoryStatus(
            @PathVariable String publicId,
            @RequestParam boolean isActive) {
        log.info("Admin updating category status: publicId={}, isActive={}", publicId, isActive);
        CategoryResponse response = categoryService.updateCategoryStatus(publicId, isActive);
        return ResponseEntity.ok(ApiResponse.success("Category status updated successfully", response));
    }
}
