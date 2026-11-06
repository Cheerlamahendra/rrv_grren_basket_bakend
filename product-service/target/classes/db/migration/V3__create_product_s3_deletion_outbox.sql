-- ==============================================================================
-- Migration: V3__create_product_s3_deletion_outbox.sql
-- Description: Create product_s3_deletion_outbox table for reliable AWS S3 image cleanup
-- ==============================================================================

CREATE TABLE IF NOT EXISTS product_s3_deletion_outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL UNIQUE,
    product_public_id VARCHAR(36) NOT NULL,
    storage_key VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 5,
    last_error VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    next_retry_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_s3_outbox_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    INDEX idx_s3_outbox_status_next_retry (status, next_retry_at),
    INDEX idx_s3_outbox_product_public_id (product_public_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
