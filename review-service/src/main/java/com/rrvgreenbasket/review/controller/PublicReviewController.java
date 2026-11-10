package com.rrvgreenbasket.review.controller;

import com.rrvgreenbasket.review.dto.ApiResponse;
import com.rrvgreenbasket.review.dto.request.BulkProductSummaryRequest;
import com.rrvgreenbasket.review.dto.response.BulkRatingSummaryItemResponse;
import com.rrvgreenbasket.review.dto.response.PageResponse;
import com.rrvgreenbasket.review.dto.response.ProductRatingSummaryResponse;
import com.rrvgreenbasket.review.dto.response.ReviewResponse;
import com.rrvgreenbasket.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@Tag(name = "Public Product Reviews", description = "Public endpoints for browsing active product reviews and aggregated ratings")
public class PublicReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "Get product reviews", description = "Returns paginated active reviews for a product with flexible sorting (newest, oldest, highest_rating, lowest_rating).")
    @GetMapping("/products/{productPublicId}")
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> getProductReviews(
            @PathVariable String productPublicId,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sorting criteria: newest, oldest, highest_rating, lowest_rating")
            @RequestParam(defaultValue = "newest") String sortBy) {

        log.debug("Fetching public reviews for product {}: page={}, size={}, sortBy={}",
                productPublicId, page, size, sortBy);

        Sort sort = switch (sortBy.toLowerCase()) {
            case "oldest" -> Sort.by(Sort.Direction.ASC, "createdAt");
            case "highest_rating", "highest" -> Sort.by(Sort.Direction.DESC, "rating").and(Sort.by(Sort.Direction.DESC, "createdAt"));
            case "lowest_rating", "lowest" -> Sort.by(Sort.Direction.ASC, "rating").and(Sort.by(Sort.Direction.DESC, "createdAt"));
            case "newest" -> Sort.by(Sort.Direction.DESC, "createdAt");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };

        Pageable pageable = PageRequest.of(page, size, sort);
        PageResponse<ReviewResponse> response = reviewService.getProductReviews(productPublicId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Product reviews retrieved successfully", response));
    }

    @Operation(summary = "Get product rating summary", description = "Returns aggregated average rating, total reviews count, and star distribution for a product.")
    @GetMapping("/products/{productPublicId}/summary")
    public ResponseEntity<ApiResponse<ProductRatingSummaryResponse>> getProductRatingSummary(
            @PathVariable String productPublicId) {

        log.debug("Fetching rating summary for product {}", productPublicId);
        ProductRatingSummaryResponse response = reviewService.getProductRatingSummary(productPublicId);
        return ResponseEntity.ok(ApiResponse.success("Rating summary retrieved successfully", response));
    }

    @Operation(summary = "Get bulk product rating summaries", description = "Returns average ratings and review counts for up to 50 products in a single database aggregation request.")
    @PostMapping("/products/summaries")
    public ResponseEntity<ApiResponse<List<BulkRatingSummaryItemResponse>>> getBulkRatingSummaries(
            @Valid @RequestBody BulkProductSummaryRequest request) {

        log.debug("Fetching bulk rating summaries for {} products", request.getProductPublicIds().size());
        List<BulkRatingSummaryItemResponse> response = reviewService.getBulkRatingSummaries(request);
        return ResponseEntity.ok(ApiResponse.success("Bulk rating summaries retrieved successfully", response));
    }
}
