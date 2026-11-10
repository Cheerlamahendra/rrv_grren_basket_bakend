package com.rrvgreenbasket.review.controller;

import com.rrvgreenbasket.review.dto.ApiResponse;
import com.rrvgreenbasket.review.dto.request.CreateReviewRequest;
import com.rrvgreenbasket.review.dto.request.UpdateReviewRequest;
import com.rrvgreenbasket.review.dto.response.PageResponse;
import com.rrvgreenbasket.review.dto.response.ReviewResponse;
import com.rrvgreenbasket.review.exception.InvalidRequestException;
import com.rrvgreenbasket.review.security.SecurityUtils;
import com.rrvgreenbasket.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@Tag(name = "Customer Review Management", description = "Endpoints for authenticated customers to submit, update, delete, and view their product reviews")
@SecurityRequirement(name = "bearerAuth")
public class CustomerReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "Submit a product review", description = "Allows an authenticated customer to submit an active rating and review for a product.")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ReviewResponse>> submitReview(
            @Valid @RequestBody CreateReviewRequest request) {

        String customerPublicId = SecurityUtils.getCurrentUserId();
        if (customerPublicId == null || customerPublicId.isBlank()) {
            throw new InvalidRequestException("Authenticated customer identity could not be resolved from token");
        }

        String displayName = SecurityUtils.getCurrentUser();
        log.info("Customer {} submitting review for product {}", customerPublicId, request.getProductPublicId());

        ReviewResponse response = reviewService.submitReview(request, customerPublicId, displayName);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Review submitted successfully", response));
    }

    @Operation(summary = "Update own product review", description = "Allows a customer to update their own active rating, title, and review comment.")
    @PutMapping("/{reviewPublicId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ReviewResponse>> updateMyReview(
            @PathVariable String reviewPublicId,
            @Valid @RequestBody UpdateReviewRequest request) {

        String customerPublicId = SecurityUtils.getCurrentUserId();
        log.info("Customer {} updating review {}", customerPublicId, reviewPublicId);

        ReviewResponse response = reviewService.updateMyReview(reviewPublicId, request, customerPublicId);
        return ResponseEntity.ok(ApiResponse.success("Review updated successfully", response));
    }

    @Operation(summary = "Delete own product review", description = "Soft deletes the customer's own review. Customers cannot delete reviews belonging to others.")
    @DeleteMapping("/{reviewPublicId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteMyReview(
            @PathVariable String reviewPublicId) {

        String customerPublicId = SecurityUtils.getCurrentUserId();
        log.info("Customer {} deleting review {}", customerPublicId, reviewPublicId);

        reviewService.deleteMyReview(reviewPublicId, customerPublicId);
        return ResponseEntity.ok(ApiResponse.success("Review deleted successfully"));
    }

    @Operation(summary = "Get my reviews", description = "Returns a paginated list of all reviews submitted by the authenticated customer.")
    @GetMapping("/my-reviews")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> getMyReviews(
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        String customerPublicId = SecurityUtils.getCurrentUserId();
        log.debug("Fetching reviews for authenticated customer {}", customerPublicId);

        PageResponse<ReviewResponse> response = reviewService.getMyReviews(customerPublicId, pageable);
        return ResponseEntity.ok(ApiResponse.success("My reviews retrieved successfully", response));
    }

    @Operation(summary = "Check if customer reviewed a product", description = "Returns whether the authenticated customer has already submitted a review for the specified product.")
    @GetMapping("/products/{productPublicId}/my-review")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ReviewResponse>> checkMyReviewForProduct(
            @PathVariable String productPublicId) {

        String customerPublicId = SecurityUtils.getCurrentUserId();
        log.debug("Checking review for customer {} on product {}", customerPublicId, productPublicId);

        ReviewResponse response = reviewService.getMyReviewForProduct(productPublicId, customerPublicId);
        if (response == null) {
            return ResponseEntity.ok(ApiResponse.success("No active review found for this product", null));
        }
        return ResponseEntity.ok(ApiResponse.success("Existing review found", response));
    }
}
