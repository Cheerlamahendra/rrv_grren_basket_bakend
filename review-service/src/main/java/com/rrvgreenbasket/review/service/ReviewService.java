package com.rrvgreenbasket.review.service;

import com.rrvgreenbasket.review.dto.request.BulkProductSummaryRequest;
import com.rrvgreenbasket.review.dto.request.CreateReviewRequest;
import com.rrvgreenbasket.review.dto.request.UpdateReviewRequest;
import com.rrvgreenbasket.review.dto.response.*;
import com.rrvgreenbasket.review.enums.ReviewStatus;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

public interface ReviewService {

    // Customer operations
    ReviewResponse submitReview(CreateReviewRequest request, String customerPublicId, String customerDisplayName);

    ReviewResponse updateMyReview(String reviewPublicId, UpdateReviewRequest request, String customerPublicId);

    void deleteMyReview(String reviewPublicId, String customerPublicId);

    PageResponse<ReviewResponse> getMyReviews(String customerPublicId, Pageable pageable);

    ReviewResponse getMyReviewForProduct(String productPublicId, String customerPublicId);

    // Public operations
    PageResponse<ReviewResponse> getProductReviews(String productPublicId, Pageable pageable);

    ProductRatingSummaryResponse getProductRatingSummary(String productPublicId);

    List<BulkRatingSummaryItemResponse> getBulkRatingSummaries(BulkProductSummaryRequest request);

    // Admin operations
    PageResponse<ReviewDetailResponse> getAllReviewsForAdmin(
            String productPublicId,
            String customerPublicId,
            Integer rating,
            ReviewStatus status,
            Instant startDate,
            Instant endDate,
            Pageable pageable);

    ReviewDetailResponse getReviewDetailsForAdmin(String reviewPublicId);

    void deleteReviewByAdmin(String reviewPublicId, String reason, String adminId);

    AdminReviewStatsResponse getAdminStatistics();
}
