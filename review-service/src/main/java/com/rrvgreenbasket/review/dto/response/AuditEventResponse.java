package com.rrvgreenbasket.review.dto.response;

import com.rrvgreenbasket.review.enums.ReviewAuditAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEventResponse {

    private Long id;
    private String reviewPublicId;
    private String actorPublicId;
    private String actorRole;
    private ReviewAuditAction action;
    private String reason;
    private Instant createdAt;
}
