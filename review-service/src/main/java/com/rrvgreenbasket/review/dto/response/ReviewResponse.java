package com.rrvgreenbasket.review.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponse {

    private String publicId;
    private String productPublicId;
    private String customerDisplayName;
    private Integer rating;
    private String reviewTitle;
    private String reviewComment;
    private Instant createdAt;
    private Instant updatedAt;
}
