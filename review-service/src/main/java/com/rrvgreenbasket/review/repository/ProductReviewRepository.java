package com.rrvgreenbasket.review.repository;

import com.rrvgreenbasket.review.entity.ProductReview;
import com.rrvgreenbasket.review.enums.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductReviewRepository extends JpaRepository<ProductReview, Long>, JpaSpecificationExecutor<ProductReview> {

    Optional<ProductReview> findByPublicId(String publicId);

    Optional<ProductReview> findByCustomerPublicIdAndProductPublicIdAndStatus(
            String customerPublicId, String productPublicId, ReviewStatus status);

    boolean existsByCustomerPublicIdAndProductPublicIdAndStatus(
            String customerPublicId, String productPublicId, ReviewStatus status);

    Page<ProductReview> findByCustomerPublicId(String customerPublicId, Pageable pageable);

    Page<ProductReview> findByProductPublicIdAndStatus(
            String productPublicId, ReviewStatus status, Pageable pageable);

    @Query("SELECT r.rating AS rating, COUNT(r.id) AS count " +
           "FROM ProductReview r " +
           "WHERE r.productPublicId = :productId AND r.status = :status " +
           "GROUP BY r.rating")
    List<RatingCountProjection> getRatingCountsForProduct(
            @Param("productId") String productId,
            @Param("status") ReviewStatus status);

    interface RatingCountProjection {
        Integer getRating();
        Long getCount();
    }

    @Query("SELECT r.productPublicId AS productPublicId, AVG(CAST(r.rating AS double)) AS averageRating, COUNT(r.id) AS totalReviews " +
           "FROM ProductReview r " +
           "WHERE r.productPublicId IN :productIds AND r.status = :status " +
           "GROUP BY r.productPublicId")
    List<BulkRatingProjection> getBulkRatingSummaries(
            @Param("productIds") List<String> productIds,
            @Param("status") ReviewStatus status);

    interface BulkRatingProjection {
        String getProductPublicId();
        Double getAverageRating();
        Long getTotalReviews();
    }

    long countByStatus(ReviewStatus status);

    @Query("SELECT AVG(CAST(r.rating AS double)) FROM ProductReview r WHERE r.status = :status")
    Double getAverageRatingByStatus(@Param("status") ReviewStatus status);

    long countByCreatedAtAfter(Instant after);

    @Query("SELECT r.rating AS rating, COUNT(r.id) AS count " +
           "FROM ProductReview r " +
           "WHERE r.status = :status " +
           "GROUP BY r.rating")
    List<RatingCountProjection> getOverallRatingDistribution(@Param("status") ReviewStatus status);
}
