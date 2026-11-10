package com.rrvgreenbasket.review.service.impl;

import com.rrvgreenbasket.review.client.ProductServiceClient;
import com.rrvgreenbasket.review.dto.request.BulkProductSummaryRequest;
import com.rrvgreenbasket.review.dto.request.CreateReviewRequest;
import com.rrvgreenbasket.review.dto.request.UpdateReviewRequest;
import com.rrvgreenbasket.review.dto.response.*;
import com.rrvgreenbasket.review.entity.ProductReview;
import com.rrvgreenbasket.review.entity.ReviewAuditEvent;
import com.rrvgreenbasket.review.enums.ReviewAuditAction;
import com.rrvgreenbasket.review.enums.ReviewStatus;
import com.rrvgreenbasket.review.exception.DuplicateResourceException;
import com.rrvgreenbasket.review.exception.ForbiddenOperationException;
import com.rrvgreenbasket.review.exception.InvalidRequestException;
import com.rrvgreenbasket.review.exception.ResourceNotFoundException;
import com.rrvgreenbasket.review.repository.ProductReviewRepository;
import com.rrvgreenbasket.review.repository.ReviewAuditEventRepository;
import com.rrvgreenbasket.review.service.ReviewService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ProductReviewRepository reviewRepository;
    private final ReviewAuditEventRepository auditEventRepository;
    private final ProductServiceClient productServiceClient;

    @Override
    @Transactional
    public ReviewResponse submitReview(CreateReviewRequest request, String customerPublicId, String customerDisplayName) {
        log.info("Customer {} submitting review for product {}", customerPublicId, request.getProductPublicId());

        // 1. Verify product existence through Product Service
        productServiceClient.validateProductForReview(request.getProductPublicId());

        // 2. Prevent duplicate active reviews
        boolean exists = reviewRepository.existsByCustomerPublicIdAndProductPublicIdAndStatus(
                customerPublicId, request.getProductPublicId(), ReviewStatus.ACTIVE);
        if (exists) {
            throw new DuplicateResourceException(
                    "You have already submitted an active review for this product. You can update your existing review.");
        }

        // 3. Format safe display name (e.g. "Mahendra K." or "Verified Customer")
        String safeDisplayName = formatSafeDisplayName(customerDisplayName);

        // 4. Create and persist review entity
        ProductReview review = ProductReview.builder()
                .productPublicId(request.getProductPublicId().trim())
                .customerPublicId(customerPublicId.trim())
                .customerDisplayName(safeDisplayName)
                .rating(request.getRating())
                .reviewTitle(request.getReviewTitle() != null ? request.getReviewTitle().trim() : null)
                .reviewComment(request.getReviewComment().trim())
                .status(ReviewStatus.ACTIVE)
                .build();

        ProductReview savedReview = reviewRepository.save(review);

        // 5. Create audit event
        recordAuditEvent(savedReview.getPublicId(), customerPublicId, "CUSTOMER", ReviewAuditAction.CREATE, "Initial review submission");

        log.info("Review created successfully: publicId={}, rating={}", savedReview.getPublicId(), savedReview.getRating());
        return mapToReviewResponse(savedReview);
    }

    @Override
    @Transactional
    public ReviewResponse updateMyReview(String reviewPublicId, UpdateReviewRequest request, String customerPublicId) {
        log.info("Customer {} updating review {}", customerPublicId, reviewPublicId);

        ProductReview review = reviewRepository.findByPublicId(reviewPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with ID: " + reviewPublicId));

        // Check ownership
        if (!review.getCustomerPublicId().equals(customerPublicId)) {
            throw new ForbiddenOperationException("Access denied: You are not authorized to update another customer's review");
        }

        // Check if review is active
        if (!review.isActive()) {
            throw new InvalidRequestException("Cannot update a deleted review. Please submit a new review instead.");
        }

        review.setRating(request.getRating());
        review.setReviewTitle(request.getReviewTitle() != null ? request.getReviewTitle().trim() : null);
        review.setReviewComment(request.getReviewComment().trim());

        ProductReview updatedReview = reviewRepository.save(review);

        recordAuditEvent(reviewPublicId, customerPublicId, "CUSTOMER", ReviewAuditAction.UPDATE, "Customer updated rating and comment");

        log.info("Review {} updated successfully by customer {}", reviewPublicId, customerPublicId);
        return mapToReviewResponse(updatedReview);
    }

    @Override
    @Transactional
    public void deleteMyReview(String reviewPublicId, String customerPublicId) {
        log.info("Customer {} deleting review {}", customerPublicId, reviewPublicId);

        ProductReview review = reviewRepository.findByPublicId(reviewPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with ID: " + reviewPublicId));

        // Check ownership
        if (!review.getCustomerPublicId().equals(customerPublicId)) {
            throw new ForbiddenOperationException("Access denied: You are not authorized to delete another customer's review");
        }

        if (!review.isActive()) {
            throw new InvalidRequestException("Review is already deleted");
        }

        review.setStatus(ReviewStatus.DELETED);
        review.setDeletedAt(Instant.now());
        review.setDeletedBy(customerPublicId);
        review.setDeletionReason("Deleted by customer");

        reviewRepository.save(review);

        recordAuditEvent(reviewPublicId, customerPublicId, "CUSTOMER", ReviewAuditAction.CUSTOMER_DELETE, "Customer deleted their own review");

        log.info("Review {} soft deleted by customer {}", reviewPublicId, customerPublicId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> getMyReviews(String customerPublicId, Pageable pageable) {
        log.debug("Fetching reviews for customer {}", customerPublicId);
        Page<ProductReview> page = reviewRepository.findByCustomerPublicId(customerPublicId, pageable);
        List<ReviewResponse> dtoList = page.getContent().stream()
                .map(this::mapToReviewResponse)
                .collect(Collectors.toList());
        return PageResponse.from(page, dtoList);
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewResponse getMyReviewForProduct(String productPublicId, String customerPublicId) {
        log.debug("Checking review for customer {} on product {}", customerPublicId, productPublicId);
        return reviewRepository.findByCustomerPublicIdAndProductPublicIdAndStatus(customerPublicId, productPublicId, ReviewStatus.ACTIVE)
                .map(this::mapToReviewResponse)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> getProductReviews(String productPublicId, Pageable pageable) {
        log.debug("Fetching public reviews for product {}", productPublicId);
        Page<ProductReview> page = reviewRepository.findByProductPublicIdAndStatus(
                productPublicId, ReviewStatus.ACTIVE, pageable);
        List<ReviewResponse> dtoList = page.getContent().stream()
                .map(this::mapToReviewResponse)
                .collect(Collectors.toList());
        return PageResponse.from(page, dtoList);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductRatingSummaryResponse getProductRatingSummary(String productPublicId) {
        log.debug("Calculating rating summary for product {}", productPublicId);

        List<ProductReviewRepository.RatingCountProjection> counts =
                reviewRepository.getRatingCountsForProduct(productPublicId, ReviewStatus.ACTIVE);

        long fiveStar = 0;
        long fourStar = 0;
        long threeStar = 0;
        long twoStar = 0;
        long oneStar = 0;
        long totalReviews = 0;
        long weightedSum = 0;

        for (ProductReviewRepository.RatingCountProjection row : counts) {
            int rating = row.getRating();
            long count = row.getCount();
            totalReviews += count;
            weightedSum += ((long) rating * count);

            switch (rating) {
                case 5 -> fiveStar = count;
                case 4 -> fourStar = count;
                case 3 -> threeStar = count;
                case 2 -> twoStar = count;
                case 1 -> oneStar = count;
            }
        }

        double averageRating = 0.0;
        Map<Integer, Double> distribution = new LinkedHashMap<>();
        if (totalReviews > 0) {
            double rawAvg = (double) weightedSum / totalReviews;
            averageRating = BigDecimal.valueOf(rawAvg).setScale(1, RoundingMode.HALF_UP).doubleValue();

            distribution.put(5, BigDecimal.valueOf((double) fiveStar * 100 / totalReviews).setScale(1, RoundingMode.HALF_UP).doubleValue());
            distribution.put(4, BigDecimal.valueOf((double) fourStar * 100 / totalReviews).setScale(1, RoundingMode.HALF_UP).doubleValue());
            distribution.put(3, BigDecimal.valueOf((double) threeStar * 100 / totalReviews).setScale(1, RoundingMode.HALF_UP).doubleValue());
            distribution.put(2, BigDecimal.valueOf((double) twoStar * 100 / totalReviews).setScale(1, RoundingMode.HALF_UP).doubleValue());
            distribution.put(1, BigDecimal.valueOf((double) oneStar * 100 / totalReviews).setScale(1, RoundingMode.HALF_UP).doubleValue());
        } else {
            distribution.put(5, 0.0);
            distribution.put(4, 0.0);
            distribution.put(3, 0.0);
            distribution.put(2, 0.0);
            distribution.put(1, 0.0);
        }

        return ProductRatingSummaryResponse.builder()
                .productPublicId(productPublicId)
                .averageRating(averageRating)
                .totalActiveReviews(totalReviews)
                .fiveStarCount(fiveStar)
                .fourStarCount(fourStar)
                .threeStarCount(threeStar)
                .twoStarCount(twoStar)
                .oneStarCount(oneStar)
                .ratingDistributionPercentages(distribution)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BulkRatingSummaryItemResponse> getBulkRatingSummaries(BulkProductSummaryRequest request) {
        List<String> productIds = request.getProductPublicIds();
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<ProductReviewRepository.BulkRatingProjection> projections =
                reviewRepository.getBulkRatingSummaries(productIds, ReviewStatus.ACTIVE);

        Map<String, BulkRatingSummaryItemResponse> resultMap = new HashMap<>();
        for (ProductReviewRepository.BulkRatingProjection p : projections) {
            double avg = p.getAverageRating() != null
                    ? BigDecimal.valueOf(p.getAverageRating()).setScale(1, RoundingMode.HALF_UP).doubleValue()
                    : 0.0;
            resultMap.put(p.getProductPublicId(), BulkRatingSummaryItemResponse.builder()
                    .productPublicId(p.getProductPublicId())
                    .averageRating(avg)
                    .totalReviews(p.getTotalReviews() != null ? p.getTotalReviews() : 0L)
                    .build());
        }

        // Ensure all requested product IDs are returned even if they have 0 reviews
        List<BulkRatingSummaryItemResponse> resultList = new ArrayList<>();
        for (String id : productIds) {
            if (resultMap.containsKey(id)) {
                resultList.add(resultMap.get(id));
            } else {
                resultList.add(BulkRatingSummaryItemResponse.builder()
                        .productPublicId(id)
                        .averageRating(0.0)
                        .totalReviews(0L)
                        .build());
            }
        }

        return resultList;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewDetailResponse> getAllReviewsForAdmin(
            String productPublicId,
            String customerPublicId,
            Integer rating,
            ReviewStatus status,
            Instant startDate,
            Instant endDate,
            Pageable pageable) {

        log.debug("Admin querying reviews: product={}, customer={}, rating={}, status={}",
                productPublicId, customerPublicId, rating, status);

        Specification<ProductReview> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (productPublicId != null && !productPublicId.isBlank()) {
                predicates.add(cb.equal(root.get("productPublicId"), productPublicId.trim()));
            }
            if (customerPublicId != null && !customerPublicId.isBlank()) {
                predicates.add(cb.equal(root.get("customerPublicId"), customerPublicId.trim()));
            }
            if (rating != null) {
                predicates.add(cb.equal(root.get("rating"), rating));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<ProductReview> page = reviewRepository.findAll(spec, pageable);
        List<ReviewDetailResponse> dtoList = page.getContent().stream()
                .map(r -> mapToReviewDetailResponse(r, Collections.emptyList()))
                .collect(Collectors.toList());

        return PageResponse.from(page, dtoList);
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewDetailResponse getReviewDetailsForAdmin(String reviewPublicId) {
        log.debug("Admin querying details for review {}", reviewPublicId);

        ProductReview review = reviewRepository.findByPublicId(reviewPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with ID: " + reviewPublicId));

        List<ReviewAuditEvent> audits = auditEventRepository.findByReviewPublicIdOrderByCreatedAtDesc(reviewPublicId);
        List<AuditEventResponse> auditResponses = audits.stream()
                .map(this::mapToAuditEventResponse)
                .collect(Collectors.toList());

        return mapToReviewDetailResponse(review, auditResponses);
    }

    @Override
    @Transactional
    public void deleteReviewByAdmin(String reviewPublicId, String reason, String adminId) {
        log.info("Admin {} deleting review {} with reason: {}", adminId, reviewPublicId, reason);

        ProductReview review = reviewRepository.findByPublicId(reviewPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with ID: " + reviewPublicId));

        if (!review.isActive()) {
            throw new InvalidRequestException("Review is already deleted");
        }

        String finalReason = (reason != null && !reason.isBlank()) ? reason.trim() : "Deleted by administrator for policy violation";

        review.setStatus(ReviewStatus.DELETED);
        review.setDeletedAt(Instant.now());
        review.setDeletedBy(adminId);
        review.setDeletionReason(finalReason);

        reviewRepository.save(review);

        recordAuditEvent(reviewPublicId, adminId, "ADMIN", ReviewAuditAction.ADMIN_DELETE, finalReason);

        log.info("Review {} soft deleted by admin {}", reviewPublicId, adminId);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminReviewStatsResponse getAdminStatistics() {
        log.debug("Computing admin review statistics");

        long totalReviews = reviewRepository.count();
        long activeReviews = reviewRepository.countByStatus(ReviewStatus.ACTIVE);
        long deletedReviews = reviewRepository.countByStatus(ReviewStatus.DELETED);

        Double rawAvg = reviewRepository.getAverageRatingByStatus(ReviewStatus.ACTIVE);
        double averageActiveRating = rawAvg != null
                ? BigDecimal.valueOf(rawAvg).setScale(1, RoundingMode.HALF_UP).doubleValue()
                : 0.0;

        List<ProductReviewRepository.RatingCountProjection> distributionProjections =
                reviewRepository.getOverallRatingDistribution(ReviewStatus.ACTIVE);

        Map<Integer, Long> distribution = new LinkedHashMap<>();
        distribution.put(5, 0L);
        distribution.put(4, 0L);
        distribution.put(3, 0L);
        distribution.put(2, 0L);
        distribution.put(1, 0L);

        for (ProductReviewRepository.RatingCountProjection row : distributionProjections) {
            distribution.put(row.getRating(), row.getCount());
        }

        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        long recentCount = reviewRepository.countByCreatedAtAfter(sevenDaysAgo);

        return AdminReviewStatsResponse.builder()
                .totalReviews(totalReviews)
                .activeReviews(activeReviews)
                .deletedReviews(deletedReviews)
                .averageActiveRating(averageActiveRating)
                .ratingDistribution(distribution)
                .recentReviewCount(recentCount)
                .build();
    }

    private void recordAuditEvent(String reviewPublicId, String actorId, String actorRole, ReviewAuditAction action, String reason) {
        ReviewAuditEvent event = ReviewAuditEvent.builder()
                .reviewPublicId(reviewPublicId)
                .actorPublicId(actorId)
                .actorRole(actorRole)
                .action(action)
                .reason(reason)
                .build();
        auditEventRepository.save(event);
    }

    private ReviewResponse mapToReviewResponse(ProductReview review) {
        return ReviewResponse.builder()
                .publicId(review.getPublicId())
                .productPublicId(review.getProductPublicId())
                .customerDisplayName(review.getCustomerDisplayName())
                .rating(review.getRating())
                .reviewTitle(review.getReviewTitle())
                .reviewComment(review.getReviewComment())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }

    private ReviewDetailResponse mapToReviewDetailResponse(ProductReview review, List<AuditEventResponse> audits) {
        return ReviewDetailResponse.builder()
                .publicId(review.getPublicId())
                .productPublicId(review.getProductPublicId())
                .customerPublicId(review.getCustomerPublicId())
                .customerDisplayName(review.getCustomerDisplayName())
                .rating(review.getRating())
                .reviewTitle(review.getReviewTitle())
                .reviewComment(review.getReviewComment())
                .status(review.getStatus())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .deletedAt(review.getDeletedAt())
                .deletedBy(review.getDeletedBy())
                .deletionReason(review.getDeletionReason())
                .auditHistory(audits)
                .build();
    }

    private AuditEventResponse mapToAuditEventResponse(ReviewAuditEvent event) {
        return AuditEventResponse.builder()
                .id(event.getId())
                .reviewPublicId(event.getReviewPublicId())
                .actorPublicId(event.getActorPublicId())
                .actorRole(event.getActorRole())
                .action(event.getAction())
                .reason(event.getReason())
                .createdAt(event.getCreatedAt())
                .build();
    }

    private String formatSafeDisplayName(String name) {
        if (name == null || name.isBlank()) {
            return "Verified Customer";
        }
        String clean = name.trim();
        if (clean.contains("@")) {
            // It's an email address - mask it
            String local = clean.substring(0, clean.indexOf("@"));
            return (local.length() > 2 ? local.substring(0, 2) + "***" : "Verified Customer");
        }
        String[] parts = clean.split("\\s+");
        if (parts.length > 1) {
            return parts[0] + " " + parts[1].substring(0, 1).toUpperCase() + ".";
        }
        return parts[0];
    }
}
