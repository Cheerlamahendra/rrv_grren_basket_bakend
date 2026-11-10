
package com.rrvgreenbasket.cart.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "cart_items",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_cart_items_public_id",
                        columnNames = "public_id"
                ),
                @UniqueConstraint(
                        name = "uk_cart_product_unit",
                        columnNames = {
                                "cart_id",
                                "product_public_id",
                                "unit_of_measure"
                        }
                )
        },
        indexes = {
                @Index(name = "idx_cart_items_cart", columnList = "cart_id"),
                @Index(name = "idx_cart_items_product", columnList = "product_public_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", length = 36, nullable = false, unique = true, columnDefinition = "CHAR(36)")
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @Column(name = "product_public_id", length = 36, nullable = false, columnDefinition = "CHAR(36)")
    private String productPublicId;

    @Column(name = "product_name_snapshot", nullable = false, length = 200)
    private String productNameSnapshot;

    @Column(name = "product_image_url_snapshot", length = 1000)
    private String productImageUrlSnapshot;

    @Column(name = "quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_of_measure", nullable = false, length = 20)
    private String unitOfMeasure;

    @Column(
            name = "price_per_kg_snapshot",
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal pricePerKgSnapshot;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void generatePublicId() {
        if (publicId == null) {
            publicId = UUID.randomUUID().toString();
        }
    }
}
