package com.rrvgreenbasket.review.controller;

import com.rrvgreenbasket.review.dto.ApiResponse;
import com.rrvgreenbasket.review.dto.response.AdminReviewStatsResponse;
import com.rrvgreenbasket.review.dto.response.PageResponse;
import com.rrvgreenbasket.review.dto.response.ReviewDetailResponse;
import com.rrvgreenbasket.review.enums.ReviewStatus;
import com.rrvgreenbasket.review.security.SecurityUtils;
import com.rrvgreenbasket.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Slf4j
@RestController
@RequestMapping("/api/admin/reviews")
@RequiredArgsConstructor
@Tag(name = "Admin Review Management", description = "Endpoints for administrators to moderate customer reviews, inspect audit events, soft-delete reviews, and analyze statistics")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPER_ADMIN', 'ADMIN')")
public class AdminReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "Get all reviews with filters & pagination", description = "Admin endpoint to filter reviews by product, customer, rating, status, and creation date range.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ReviewDetailResponse>>> getAllReviews(
            @Parameter(description = "Filter by product public UUID") @RequestParam(required = false) String productPublicId,
            @Parameter(description = "Filter by customer public UUID") @RequestParam(required = false) String customerPublicId,
            @Parameter(description = "Filter by rating (1-5)") @RequestParam(required = false) Integer rating,
            @Parameter(description = "Filter by status (ACTIVE, DELETED)") @RequestParam(required = false) ReviewStatus status,
            @Parameter(description = "Filter by creation start date (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "Filter by creation end date (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "Sort direction: newest (default) or oldest") @RequestParam(defaultValue = "newest") String sort,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size) {

        log.debug("Admin searching reviews: product={}, customer={}, rating={}, status={}, sort={}",
                productPublicId, customerPublicId, rating, status, sort);

        Sort sorting = "oldest".equalsIgnoreCase(sort)
                ? Sort.by(Sort.Direction.ASC, "createdAt")
                : Sort.by(Sort.Direction.DESC, "createdAt");

        Pageable pageable = PageRequest.of(page, size, sorting);

        Instant startInstant = (startDate != null) ? startDate.atStartOfDay().toInstant(ZoneOffset.UTC) : null;
        Instant endInstant = (endDate != null) ? endDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC) : null;

        PageResponse<ReviewDetailResponse> response = reviewService.getAllReviewsForAdmin(
                productPublicId, customerPublicId, rating, status, startInstant, endInstant, pageable);

        return ResponseEntity.ok(ApiResponse.success("Reviews retrieved successfully", response));
    }

    @Operation(summary = "Get review details with audit history", description = "Returns full details for a review, including customer public ID, status, and chronological audit log.")
    @GetMapping("/{reviewPublicId}")
    public ResponseEntity<ApiResponse<ReviewDetailResponse>> getReviewDetails(
            @PathVariable String reviewPublicId) {

        log.debug("Admin fetching details for review {}", reviewPublicId);
        ReviewDetailResponse response = reviewService.getReviewDetailsForAdmin(reviewPublicId);
        return ResponseEntity.ok(ApiResponse.success("Review details retrieved successfully", response));
    }

    @Operation(summary = "Soft delete any customer review", description = "Allows an administrator to soft-delete an inappropriate review with a mandatory or optional reason.")
    @DeleteMapping("/{reviewPublicId}")
    public ResponseEntity<ApiResponse<Void>> deleteReview(
            @PathVariable String reviewPublicId,
            @Parameter(description = "Optional reason for deletion") @RequestParam(required = false) String reason) {

        String adminId = SecurityUtils.getCurrentUserId();
        if (adminId == null || adminId.isBlank()) {
            adminId = "ADMIN";
        }

        log.info("Admin {} deleting review {} with reason: {}", adminId, reviewPublicId, reason);
        reviewService.deleteReviewByAdmin(reviewPublicId, reason, adminId);
        return ResponseEntity.ok(ApiResponse.success("Review soft deleted successfully"));
    }

    @Operation(summary = "Get review statistics", description = "Returns review metrics including total, active, deleted count, average rating, star distribution, and recent reviews.")
    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<AdminReviewStatsResponse>> getReviewStatistics() {
        log.debug("Admin requesting review statistics");
        AdminReviewStatsResponse response = reviewService.getAdminStatistics();
        return ResponseEntity.ok(ApiResponse.success("Review statistics retrieved successfully", response));
    }
}
