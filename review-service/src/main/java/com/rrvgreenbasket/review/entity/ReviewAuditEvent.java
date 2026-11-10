package com.rrvgreenbasket.review.entity;

import com.rrvgreenbasket.review.enums.ReviewAuditAction;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "review_audit_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "review_public_id", nullable = false, length = 36)
    private String reviewPublicId;

    @Column(name = "actor_public_id", nullable = false, length = 50)
    private String actorPublicId;

    @Column(name = "actor_role", nullable = false, length = 50)
    private String actorRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 50)
    private ReviewAuditAction action;

    @Column(name = "reason", length = 255)
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
