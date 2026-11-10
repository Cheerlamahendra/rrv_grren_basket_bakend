
package com.rrvgreenbasket.cart.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "carts",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_carts_public_id",
                        columnNames = "public_id"
                ),
                @UniqueConstraint(
                        name = "uk_carts_active_customer",
                        columnNames = "active_customer_public_id"
                )
        },
        indexes = {
                @Index(name = "idx_carts_customer", columnList = "customer_public_id"),
                @Index(name = "idx_carts_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", length = 36, nullable = false, unique = true, columnDefinition = "CHAR(36)")
    private String publicId;

    @Column(name = "customer_public_id", length = 36, nullable = false, columnDefinition = "CHAR(36)")
    private String customerPublicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CartStatus status = CartStatus.ACTIVE;

    @Column(
            name = "active_customer_public_id",
            insertable = false,
            updatable = false,
            length = 36,
            columnDefinition = "CHAR(36)"
    )
    private String activeCustomerPublicId;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @OneToMany(
            mappedBy = "cart",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<CartItem> items = new ArrayList<>();

    @PrePersist
    public void generatePublicId() {
        if (publicId == null) {
            publicId = UUID.randomUUID().toString();
        }
    }

    public void addItem(CartItem item) {
        items.add(item);
        item.setCart(this);
    }

    public void removeItem(CartItem item) {
        items.remove(item);
        item.setCart(null);
    }
}
