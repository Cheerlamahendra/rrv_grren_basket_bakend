package com.rrvgreenbasket.review.service;

import com.rrvgreenbasket.review.client.ProductServiceClient;
import com.rrvgreenbasket.review.dto.client.ProductDto;
import com.rrvgreenbasket.review.dto.request.BulkProductSummaryRequest;
import com.rrvgreenbasket.review.dto.request.CreateReviewRequest;
import com.rrvgreenbasket.review.dto.request.UpdateReviewRequest;
import com.rrvgreenbasket.review.dto.response.*;
import com.rrvgreenbasket.review.entity.ProductReview;
import com.rrvgreenbasket.review.enums.ReviewAuditAction;
import com.rrvgreenbasket.review.enums.ReviewStatus;
import com.rrvgreenbasket.review.exception.DuplicateResourceException;
import com.rrvgreenbasket.review.exception.ForbiddenOperationException;
import com.rrvgreenbasket.review.exception.ResourceNotFoundException;
import com.rrvgreenbasket.review.repository.ProductReviewRepository;
import com.rrvgreenbasket.review.repository.ReviewAuditEventRepository;
import com.rrvgreenbasket.review.service.impl.ReviewServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ProductReviewRepository reviewRepository;

    @Mock
    private ReviewAuditEventRepository auditEventRepository;

    @Mock
    private ProductServiceClient productServiceClient;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    private static final String PRODUCT_ID = "prod-1111-2222";
    private static final String CUSTOMER_ID = "cust-aaaa-bbbb";
    private static final String OTHER_CUSTOMER_ID = "cust-cccc-dddd";
    private static final String REVIEW_ID = "rev-9999-0000";

    private ProductReview activeReview;

    @BeforeEach
    void setUp() {
        activeReview = ProductReview.builder()
                .id(1L)
                .publicId(REVIEW_ID)
                .productPublicId(PRODUCT_ID)
                .customerPublicId(CUSTOMER_ID)
                .customerDisplayName("Mahendra K.")
                .rating(5)
                .reviewTitle("Fresh and crispy")
                .reviewComment("Great quality tomatoes delivered fresh.")
                .status(ReviewStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Submit review - Success")
    void testSubmitReview_Success() {
        CreateReviewRequest request = CreateReviewRequest.builder()
                .productPublicId(PRODUCT_ID)
                .rating(5)
                .reviewTitle("Excellent")
                .reviewComment("Very fresh greens!")
                .build();

        ProductDto mockProduct = ProductDto.builder()
                .publicId(PRODUCT_ID)
                .name("Tomato")
                .status("ACTIVE")
                .adminEnabled(true)
                .pricePerKg(BigDecimal.valueOf(40))
                .build();

        when(productServiceClient.validateProductForReview(PRODUCT_ID)).thenReturn(mockProduct);
        when(reviewRepository.existsByCustomerPublicIdAndProductPublicIdAndStatus(CUSTOMER_ID, PRODUCT_ID, ReviewStatus.ACTIVE))
                .thenReturn(false);
        when(reviewRepository.save(any(ProductReview.class))).thenAnswer(invocation -> {
            ProductReview r = invocation.getArgument(0);
            r.setId(10L);
            r.setPublicId(UUID.randomUUID().toString());
            r.setCreatedAt(Instant.now());
            r.setUpdatedAt(Instant.now());
            return r;
        });

        ReviewResponse response = reviewService.submitReview(request, CUSTOMER_ID, "Mahendra Kumar");

        assertThat(response).isNotNull();
        assertThat(response.getRating()).isEqualTo(5);
        assertThat(response.getCustomerDisplayName()).isEqualTo("Mahendra K.");
        verify(auditEventRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Submit review - Duplicate active review throws Conflict")
    void testSubmitReview_Duplicate() {
        CreateReviewRequest request = CreateReviewRequest.builder()
                .productPublicId(PRODUCT_ID)
                .rating(4)
                .reviewComment("Good")
                .build();

        when(productServiceClient.validateProductForReview(PRODUCT_ID)).thenReturn(new ProductDto());
        when(reviewRepository.existsByCustomerPublicIdAndProductPublicIdAndStatus(CUSTOMER_ID, PRODUCT_ID, ReviewStatus.ACTIVE))
                .thenReturn(true);

        assertThatThrownBy(() -> reviewService.submitReview(request, CUSTOMER_ID, "Mahendra"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already submitted an active review");
    }

    @Test
    @DisplayName("Update my review - Success")
    void testUpdateMyReview_Success() {
        UpdateReviewRequest updateRequest = UpdateReviewRequest.builder()
                .rating(4)
                .reviewTitle("Updated title")
                .reviewComment("Still good")
                .build();

        when(reviewRepository.findByPublicId(REVIEW_ID)).thenReturn(Optional.of(activeReview));
        when(reviewRepository.save(any(ProductReview.class))).thenReturn(activeReview);

        ReviewResponse response = reviewService.updateMyReview(REVIEW_ID, updateRequest, CUSTOMER_ID);

        assertThat(response).isNotNull();
        assertThat(activeReview.getRating()).isEqualTo(4);
        verify(auditEventRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Update my review - Attempt to update another customer's review throws Forbidden")
    void testUpdateMyReview_Forbidden() {
        UpdateReviewRequest updateRequest = UpdateReviewRequest.builder()
                .rating(4)
                .reviewComment("Hacked")
                .build();

        when(reviewRepository.findByPublicId(REVIEW_ID)).thenReturn(Optional.of(activeReview));

        assertThatThrownBy(() -> reviewService.updateMyReview(REVIEW_ID, updateRequest, OTHER_CUSTOMER_ID))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("not authorized to update another customer's review");
    }

    @Test
    @DisplayName("Delete my review - Soft delete sets status DELETED and audit event")
    void testDeleteMyReview_Success() {
        when(reviewRepository.findByPublicId(REVIEW_ID)).thenReturn(Optional.of(activeReview));
        when(reviewRepository.save(any(ProductReview.class))).thenReturn(activeReview);

        reviewService.deleteMyReview(REVIEW_ID, CUSTOMER_ID);

        assertThat(activeReview.getStatus()).isEqualTo(ReviewStatus.DELETED);
        assertThat(activeReview.getDeletedBy()).isEqualTo(CUSTOMER_ID);
        assertThat(activeReview.getDeletedAt()).isNotNull();
        verify(auditEventRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Admin delete customer review with reason")
    void testDeleteReviewByAdmin() {
        when(reviewRepository.findByPublicId(REVIEW_ID)).thenReturn(Optional.of(activeReview));
        when(reviewRepository.save(any(ProductReview.class))).thenReturn(activeReview);

        reviewService.deleteReviewByAdmin(REVIEW_ID, "Inappropriate content", "ADMIN_001");

        assertThat(activeReview.getStatus()).isEqualTo(ReviewStatus.DELETED);
        assertThat(activeReview.getDeletedBy()).isEqualTo("ADMIN_001");
        assertThat(activeReview.getDeletionReason()).isEqualTo("Inappropriate content");
        verify(auditEventRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Product rating summary calculation")
    void testGetProductRatingSummary() {
        ProductReviewRepository.RatingCountProjection p5 = mock(ProductReviewRepository.RatingCountProjection.class);
        when(p5.getRating()).thenReturn(5);
        when(p5.getCount()).thenReturn(10L);

        ProductReviewRepository.RatingCountProjection p4 = mock(ProductReviewRepository.RatingCountProjection.class);
        when(p4.getRating()).thenReturn(4);
        when(p4.getCount()).thenReturn(2L);

        when(reviewRepository.getRatingCountsForProduct(PRODUCT_ID, ReviewStatus.ACTIVE))
                .thenReturn(List.of(p5, p4));

        ProductRatingSummaryResponse summary = reviewService.getProductRatingSummary(PRODUCT_ID);

        assertThat(summary).isNotNull();
        assertThat(summary.getTotalActiveReviews()).isEqualTo(12L);
        assertThat(summary.getFiveStarCount()).isEqualTo(10L);
        assertThat(summary.getFourStarCount()).isEqualTo(2L);
        // (10*5 + 2*4) / 12 = 58 / 12 = 4.833... -> 4.8
        assertThat(summary.getAverageRating()).isEqualTo(4.8);
    }

    @Test
    @DisplayName("Bulk product rating summaries")
    void testGetBulkRatingSummaries() {
        ProductReviewRepository.BulkRatingProjection bp1 = mock(ProductReviewRepository.BulkRatingProjection.class);
        when(bp1.getProductPublicId()).thenReturn("prod-1");
        when(bp1.getAverageRating()).thenReturn(4.5);
        when(bp1.getTotalReviews()).thenReturn(20L);

        when(reviewRepository.getBulkRatingSummaries(List.of("prod-1", "prod-2"), ReviewStatus.ACTIVE))
                .thenReturn(List.of(bp1));

        BulkProductSummaryRequest request = BulkProductSummaryRequest.builder()
                .productPublicIds(List.of("prod-1", "prod-2"))
                .build();

        List<BulkRatingSummaryItemResponse> summaries = reviewService.getBulkRatingSummaries(request);

        assertThat(summaries).hasSize(2);
        assertThat(summaries.get(0).getProductPublicId()).isEqualTo("prod-1");
        assertThat(summaries.get(0).getAverageRating()).isEqualTo(4.5);
        assertThat(summaries.get(1).getProductPublicId()).isEqualTo("prod-2");
        assertThat(summaries.get(1).getAverageRating()).isEqualTo(0.0);
    }
}
