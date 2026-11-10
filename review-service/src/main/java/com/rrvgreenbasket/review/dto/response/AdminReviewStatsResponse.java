package com.rrvgreenbasket.review.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminReviewStatsResponse {

    private Long totalReviews;
    private Long activeReviews;
    private Long deletedReviews;
    private Double averageActiveRating;
    private Map<Integer, Long> ratingDistribution;
    private Long recentReviewCount;
}
