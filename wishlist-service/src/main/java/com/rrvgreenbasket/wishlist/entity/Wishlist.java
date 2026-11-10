package com.rrvgreenbasket.wishlist.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "wishlists")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wishlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wishlist_public_id", nullable = false, unique = true, length = 36)
    private String wishlistPublicId;

    @Column(name = "customer_public_id", nullable = false, unique = true, length = 36)
    private String customerPublicId;

    @Builder.Default
    @OneToMany(mappedBy = "wishlist", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<WishlistItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.wishlistPublicId == null) {
            this.wishlistPublicId = UUID.randomUUID().toString();
        }
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
        if (this.items == null) {
            this.items = new ArrayList<>();
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public void addItem(WishlistItem item) {
        if (this.items == null) {
            this.items = new ArrayList<>();
        }
        item.setWishlist(this);
        this.items.add(item);
    }

    public boolean removeItem(String productPublicId) {
        if (this.items == null || productPublicId == null) {
            return false;
        }
        return this.items.removeIf(item -> productPublicId.equalsIgnoreCase(item.getProductPublicId()));
    }

    public void clearItems() {
        if (this.items != null) {
            this.items.clear();
        }
    }

    public boolean containsProduct(String productPublicId) {
        if (this.items == null || productPublicId == null) {
            return false;
        }
        return this.items.stream()
                .anyMatch(item -> productPublicId.equalsIgnoreCase(item.getProductPublicId()));
    }

    public int getItemCount() {
        return this.items != null ? this.items.size() : 0;
    }
}
