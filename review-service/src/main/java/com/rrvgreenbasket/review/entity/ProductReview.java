package com.rrvgreenbasket.review.entity;

import com.rrvgreenbasket.review.enums.ReviewStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "product_public_id", nullable = false, length = 36)
    private String productPublicId;

    @Column(name = "customer_public_id", nullable = false, length = 36)
    private String customerPublicId;

    @Column(name = "customer_display_name", length = 100)
    private String customerDisplayName;

    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "review_title", length = 150)
    private String reviewTitle;

    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ReviewStatus status = ReviewStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by", length = 50)
    private String deletedBy;

    @Column(name = "deletion_reason", length = 255)
    private String deletionReason;

    @PrePersist
    public void prePersist() {
        if (this.publicId == null || this.publicId.isBlank()) {
            this.publicId = UUID.randomUUID().toString();
        }
        if (this.status == null) {
            this.status = ReviewStatus.ACTIVE;
        }
    }

    public boolean isActive() {
        return ReviewStatus.ACTIVE.equals(this.status);
    }
}
