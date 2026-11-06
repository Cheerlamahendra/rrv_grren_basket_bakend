package com.rrvgreenbasket.product.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_images", indexes = {
        @Index(name = "idx_product_images_product_id", columnList = "product_id"),
        @Index(name = "idx_product_images_is_primary", columnList = "is_primary")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36, updatable = false)
    @Builder.Default
    private String publicId = UUID.randomUUID().toString();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "storage_provider", nullable = false, length = 50)
    @Builder.Default
    private String storageProvider = "aws-s3";

    @Column(name = "storage_key", nullable = false, length = 255)
    private String storageKey;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "alt_text", length = 200)
    private String altText;

    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private Boolean isPrimary = false;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void ensurePublicId() {
        if (this.publicId == null || this.publicId.isBlank()) {
            this.publicId = UUID.randomUUID().toString();
        }
        if (this.storageProvider == null || this.storageProvider.isBlank()) {
            this.storageProvider = "aws-s3";
        }
        if (this.isPrimary == null) {
            this.isPrimary = false;
        }
        if (this.sortOrder == null) {
            this.sortOrder = 0;
        }
    }
}
