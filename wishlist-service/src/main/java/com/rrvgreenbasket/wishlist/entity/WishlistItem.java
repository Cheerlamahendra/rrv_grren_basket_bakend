package com.rrvgreenbasket.wishlist.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "wishlist_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WishlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wishlist_id", nullable = false)
    private Wishlist wishlist;

    @Column(name = "product_public_id", nullable = false, length = 36)
    private String productPublicId;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    @PrePersist
    public void prePersist() {
        if (this.addedAt == null) {
            this.addedAt = Instant.now();
        }
    }
}
