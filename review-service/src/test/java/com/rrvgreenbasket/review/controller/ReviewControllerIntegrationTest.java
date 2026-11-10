package com.rrvgreenbasket.review.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.review.client.ProductServiceClient;
import com.rrvgreenbasket.review.dto.client.ProductDto;
import com.rrvgreenbasket.review.dto.request.BulkProductSummaryRequest;
import com.rrvgreenbasket.review.dto.request.CreateReviewRequest;
import com.rrvgreenbasket.review.dto.request.UpdateReviewRequest;
import com.rrvgreenbasket.review.entity.ProductReview;
import com.rrvgreenbasket.review.enums.ReviewStatus;
import com.rrvgreenbasket.review.repository.ProductReviewRepository;
import com.rrvgreenbasket.review.repository.ReviewAuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductReviewRepository reviewRepository;

    @Autowired
    private ReviewAuditEventRepository auditEventRepository;

    @MockBean
    private ProductServiceClient productServiceClient;

    private static final String PRODUCT_ID_1 = "prod-uuid-1111";
    private static final String PRODUCT_ID_2 = "prod-uuid-2222";
    private static final String CUSTOMER_ID_1 = "cust-uuid-aaaa";
    private static final String CUSTOMER_ID_2 = "cust-uuid-bbbb";

    @BeforeEach
    void setUp() {
        auditEventRepository.deleteAll();
        reviewRepository.deleteAll();

        ProductDto validProduct = ProductDto.builder()
                .publicId(PRODUCT_ID_1)
                .name("Organic Tomato")
                .status("ACTIVE")
                .adminEnabled(true)
                .pricePerKg(BigDecimal.valueOf(45.00))
                .build();
        when(productServiceClient.validateProductForReview(anyString())).thenReturn(validProduct);
    }

    @Test
    @DisplayName("1. Public user gets public product reviews and rating summary")
    void testPublicReviewEndpoints() throws Exception {
        ProductReview review = ProductReview.builder()
                .publicId(UUID.randomUUID().toString())
                .productPublicId(PRODUCT_ID_1)
                .customerPublicId(CUSTOMER_ID_1)
                .customerDisplayName("Ramesh K.")
                .rating(5)
                .reviewTitle("Great taste")
                .reviewComment("Very fresh tomatoes.")
                .status(ReviewStatus.ACTIVE)
                .build();
        reviewRepository.save(review);

        // GET reviews
        mockMvc.perform(get("/api/reviews/products/" + PRODUCT_ID_1)
                        .param("sortBy", "newest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].rating").value(5))
                .andExpect(jsonPath("$.data.content[0].customerDisplayName").value("Ramesh K."));

        // GET summary
        mockMvc.perform(get("/api/reviews/products/" + PRODUCT_ID_1 + "/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.averageRating").value(5.0))
                .andExpect(jsonPath("$.data.totalActiveReviews").value(1));

        // POST bulk summaries
        BulkProductSummaryRequest bulkReq = BulkProductSummaryRequest.builder()
                .productPublicIds(List.of(PRODUCT_ID_1, PRODUCT_ID_2))
                .build();

        mockMvc.perform(post("/api/reviews/products/summaries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bulkReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].averageRating").value(5.0))
                .andExpect(jsonPath("$.data[1].averageRating").value(0.0));
    }

    @Test
    @DisplayName("2. Customer submits review, updates own review, and prevents duplicate active review")
    void testCustomerReviewFlow() throws Exception {
        CreateReviewRequest createReq = CreateReviewRequest.builder()
                .productPublicId(PRODUCT_ID_1)
                .rating(5)
                .reviewTitle("Excellent spinach")
                .reviewComment("Crispy and organic fresh!")
                .build();

        // Submit review
        String responseJson = mockMvc.perform(post("/api/reviews")
                        .header("X-User-Id", CUSTOMER_ID_1)
                        .header("X-User-Role", "CUSTOMER")
                        .header("X-User-Email", "ramesh@example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.rating").value(5))
                .andReturn().getResponse().getContentAsString();

        String reviewPublicId = objectMapper.readTree(responseJson).path("data").path("publicId").asText();

        // Attempt duplicate review -> returns 409 Conflict
        mockMvc.perform(post("/api/reviews")
                        .header("X-User-Id", CUSTOMER_ID_1)
                        .header("X-User-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));

        // Update own review
        UpdateReviewRequest updateReq = UpdateReviewRequest.builder()
                .rating(4)
                .reviewTitle("Updated title")
                .reviewComment("Still good.")
                .build();

        mockMvc.perform(put("/api/reviews/" + reviewPublicId)
                        .header("X-User-Id", CUSTOMER_ID_1)
                        .header("X-User-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.rating").value(4));

        // Attempt to update by another customer -> 403 Forbidden
        mockMvc.perform(put("/api/reviews/" + reviewPublicId)
                        .header("X-User-Id", CUSTOMER_ID_2)
                        .header("X-User-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden());

        // Get my reviews
        mockMvc.perform(get("/api/reviews/my-reviews")
                        .header("X-User-Id", CUSTOMER_ID_1)
                        .header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        // Check my review for product
        mockMvc.perform(get("/api/reviews/products/" + PRODUCT_ID_1 + "/my-review")
                        .header("X-User-Id", CUSTOMER_ID_1)
                        .header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicId").value(reviewPublicId));
    }

    @Test
    @DisplayName("3. Customer deletes own review; deleted review is excluded from public API")
    void testCustomerDeleteReview() throws Exception {
        ProductReview review = ProductReview.builder()
                .publicId(UUID.randomUUID().toString())
                .productPublicId(PRODUCT_ID_1)
                .customerPublicId(CUSTOMER_ID_1)
                .customerDisplayName("Customer A")
                .rating(5)
                .reviewComment("Temporary comment")
                .status(ReviewStatus.ACTIVE)
                .build();
        review = reviewRepository.save(review);

        // Delete own review
        mockMvc.perform(delete("/api/reviews/" + review.getPublicId())
                        .header("X-User-Id", CUSTOMER_ID_1)
                        .header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Verify soft-deleted
        ProductReview deleted = reviewRepository.findByPublicId(review.getPublicId()).orElseThrow();
        assertThat(deleted.getStatus()).isEqualTo(ReviewStatus.DELETED);

        // Verify excluded from public results
        mockMvc.perform(get("/api/reviews/products/" + PRODUCT_ID_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("4. Admin review management: list, filter, details, statistics, and admin delete")
    void testAdminReviewEndpoints() throws Exception {
        ProductReview review = ProductReview.builder()
                .publicId(UUID.randomUUID().toString())
                .productPublicId(PRODUCT_ID_1)
                .customerPublicId(CUSTOMER_ID_1)
                .customerDisplayName("Customer A")
                .rating(1)
                .reviewTitle("Bad")
                .reviewComment("Rotten apples")
                .status(ReviewStatus.ACTIVE)
                .build();
        review = reviewRepository.save(review);

        // Admin list all reviews
        mockMvc.perform(get("/api/admin/reviews")
                        .header("X-User-Id", "ADMIN_1")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        // Admin statistics
        mockMvc.perform(get("/api/admin/reviews/statistics")
                        .header("X-User-Id", "ADMIN_1")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalReviews").value(1))
                .andExpect(jsonPath("$.data.activeReviews").value(1));

        // Admin get details
        mockMvc.perform(get("/api/admin/reviews/" + review.getPublicId())
                        .header("X-User-Id", "ADMIN_1")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicId").value(review.getPublicId()));

        // Admin delete customer review
        mockMvc.perform(delete("/api/admin/reviews/" + review.getPublicId())
                        .param("reason", "Violated community guidelines")
                        .header("X-User-Id", "ADMIN_1")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk());

        ProductReview afterAdminDelete = reviewRepository.findByPublicId(review.getPublicId()).orElseThrow();
        assertThat(afterAdminDelete.getStatus()).isEqualTo(ReviewStatus.DELETED);
        assertThat(afterAdminDelete.getDeletionReason()).isEqualTo("Violated community guidelines");
    }

    @Test
    @DisplayName("5. Customer forbidden from accessing admin endpoints")
    void testCustomerCannotAccessAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/reviews")
                        .header("X-User-Id", CUSTOMER_ID_1)
                        .header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isForbidden());
    }
}
