-- ==============================================================================
-- RRV Green Basket — Wishlist Service Database Migration V1
-- Target Database: wishlist_db (MySQL 9 / H2 Test Compatibility)
-- ==============================================================================

-- 1. Table: wishlists
CREATE TABLE wishlists (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    wishlist_public_id VARCHAR(36) NOT NULL,
    customer_public_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_wishlists_public_id UNIQUE (wishlist_public_id),
    CONSTRAINT uk_wishlists_customer UNIQUE (customer_public_id)
);

CREATE INDEX idx_wishlists_customer ON wishlists (customer_public_id);

-- 2. Table: wishlist_items
CREATE TABLE wishlist_items (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    wishlist_id BIGINT NOT NULL,
    product_public_id VARCHAR(36) NOT NULL,
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wishlist_items_wishlist FOREIGN KEY (wishlist_id) REFERENCES wishlists (id) ON DELETE CASCADE,
    CONSTRAINT uk_wishlist_product UNIQUE (wishlist_id, product_public_id)
);

CREATE INDEX idx_wishlist_items_product ON wishlist_items (product_public_id);
CREATE INDEX idx_wishlist_items_wishlist ON wishlist_items (wishlist_id);
