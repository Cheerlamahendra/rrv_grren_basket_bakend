package com.rrvgreenbasket.review.dto.response;

import com.rrvgreenbasket.review.enums.ReviewStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDetailResponse {

    private String publicId;
    private String productPublicId;
    private String customerPublicId;
    private String customerDisplayName;
    private Integer rating;
    private String reviewTitle;
    private String reviewComment;
    private ReviewStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
    private String deletedBy;
    private String deletionReason;
    private List<AuditEventResponse> auditHistory;
}
