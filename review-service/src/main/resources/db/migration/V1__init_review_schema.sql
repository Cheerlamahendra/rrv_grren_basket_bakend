-- ==============================================================================
-- RRV Green Basket — Review Service Database Migration V1
-- Target Database: review_db (MySQL 9 / H2 Test Compatibility)
-- ==============================================================================

-- 1. Table: product_reviews
CREATE TABLE product_reviews (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL,
    product_public_id VARCHAR(36) NOT NULL,
    customer_public_id VARCHAR(36) NOT NULL,
    customer_display_name VARCHAR(100) NULL,
    rating INT NOT NULL,
    review_title VARCHAR(150) NULL,
    review_comment TEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    deleted_by VARCHAR(50) NULL,
    deletion_reason VARCHAR(255) NULL,
    CONSTRAINT uk_reviews_public_id UNIQUE (public_id),
    CONSTRAINT chk_reviews_rating CHECK (rating >= 1 AND rating <= 5)
);

CREATE INDEX idx_reviews_product_status ON product_reviews (product_public_id, status);
CREATE INDEX idx_reviews_customer ON product_reviews (customer_public_id);
CREATE INDEX idx_reviews_customer_product_status ON product_reviews (customer_public_id, product_public_id, status);
CREATE INDEX idx_reviews_created_at ON product_reviews (created_at);
CREATE INDEX idx_reviews_rating ON product_reviews (rating);

-- 2. Table: review_audit_events
CREATE TABLE review_audit_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    review_public_id VARCHAR(36) NOT NULL,
    actor_public_id VARCHAR(50) NOT NULL,
    actor_role VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    reason VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_review_public_id ON review_audit_events (review_public_id);
CREATE INDEX idx_audit_created_at ON review_audit_events (created_at);
