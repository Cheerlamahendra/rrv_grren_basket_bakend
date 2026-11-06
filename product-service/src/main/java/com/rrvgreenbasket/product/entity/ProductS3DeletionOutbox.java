package com.rrvgreenbasket.product.entity;

import com.rrvgreenbasket.product.enums.S3DeletionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_s3_deletion_outbox", indexes = {
        @Index(name = "idx_s3_outbox_status_next_retry", columnList = "status, next_retry_at"),
        @Index(name = "idx_s3_outbox_product_public_id", columnList = "product_public_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductS3DeletionOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36, updatable = false)
    @Builder.Default
    private String publicId = UUID.randomUUID().toString();

    @Column(name = "product_public_id", nullable = false, length = 36)
    private String productPublicId;

    @Column(name = "storage_key", nullable = false, length = 255)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private S3DeletionStatus status = S3DeletionStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    @Builder.Default
    private Integer maxRetries = 5;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "next_retry_at", nullable = false)
    @Builder.Default
    private Instant nextRetryAt = Instant.now();
}
