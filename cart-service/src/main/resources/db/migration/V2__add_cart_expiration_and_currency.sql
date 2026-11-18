-- ============================================
-- RRV Green Basket - Cart Service
-- Migration: V2
-- Database: cart_db
-- Description: Add cart expiration timestamp, currency, and EXPIRED lifecycle status
-- ============================================

-- 1. Add currency column (default INR)
ALTER TABLE carts
    ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'INR' AFTER status;

-- 2. Add expires_at column for TTL and abandoned cart cleanup
ALTER TABLE carts
    ADD COLUMN expires_at DATETIME NULL AFTER currency;

-- 3. Add composite index for efficient expiration cleanup queries
ALTER TABLE carts
    ADD INDEX idx_carts_expires_at (expires_at, status);

-- 4. Update status check constraint to include EXPIRED lifecycle state
ALTER TABLE carts
    DROP CHECK chk_carts_status;

ALTER TABLE carts
    ADD CONSTRAINT chk_carts_status
        CHECK (status IN ('ACTIVE', 'CHECKED_OUT', 'ABANDONED', 'EXPIRED'));
