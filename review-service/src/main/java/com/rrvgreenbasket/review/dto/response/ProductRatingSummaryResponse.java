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
public class ProductRatingSummaryResponse {

    private String productPublicId;
    private Double averageRating;
    private Long totalActiveReviews;
    private Long fiveStarCount;
    private Long fourStarCount;
    private Long threeStarCount;
    private Long twoStarCount;
    private Long oneStarCount;
    private Map<Integer, Double> ratingDistributionPercentages;
}
