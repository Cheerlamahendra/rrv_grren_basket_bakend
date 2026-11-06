package com.rrvgreenbasket.product.entity;

import com.rrvgreenbasket.product.enums.ProductStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_products_category_id", columnList = "category_id"),
        @Index(name = "idx_products_slug", columnList = "slug"),
        @Index(name = "idx_products_status", columnList = "status"),
        @Index(name = "idx_products_admin_enabled", columnList = "admin_enabled"),
        @Index(name = "idx_products_is_featured", columnList = "is_featured"),
        @Index(name = "idx_products_name", columnList = "name")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36, updatable = false)
    @Builder.Default
    private String publicId = UUID.randomUUID().toString();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ProductCategory category;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 150)
    private String slug;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "short_description", length = 300)
    private String shortDescription;

    @Column(name = "price_per_kg", nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerKg;

    @Column(name = "default_quantity_kg", nullable = false, precision = 10, scale = 3)
    @Builder.Default
    private BigDecimal defaultQuantityKg = new BigDecimal("1.000");

    @Column(name = "quantity_unit", nullable = false, length = 20)
    @Builder.Default
    private String quantityUnit = "KG";

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ProductStatus status = ProductStatus.DRAFT;

    @Column(name = "admin_enabled", nullable = false)
    @Builder.Default
    private Boolean adminEnabled = true;

    @Column(name = "is_featured", nullable = false)
    @Builder.Default
    private Boolean isFeatured = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC, id ASC")
    @Builder.Default
    private List<ProductImage> images = new ArrayList<>();

    @PrePersist
    public void ensurePublicId() {
        if (this.publicId == null || this.publicId.isBlank()) {
            this.publicId = UUID.randomUUID().toString();
        }
        if (this.defaultQuantityKg == null) {
            this.defaultQuantityKg = new BigDecimal("1.000");
        }
        if (this.quantityUnit == null || this.quantityUnit.isBlank()) {
            this.quantityUnit = "KG";
        }
        if (this.adminEnabled == null) {
            this.adminEnabled = true;
        }
        if (this.isFeatured == null) {
            this.isFeatured = false;
        }
        if (this.status == null) {
            this.status = ProductStatus.DRAFT;
        }
    }

    public void addImage(ProductImage image) {
        images.add(image);
        image.setProduct(this);
    }

    public void removeImage(ProductImage image) {
        images.remove(image);
        image.setProduct(null);
    }
}
