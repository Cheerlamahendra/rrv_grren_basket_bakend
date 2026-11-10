package com.rrvgreenbasket.review.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkRatingSummaryItemResponse {

    private String productPublicId;
    private Double averageRating;
    private Long totalReviews;
}
