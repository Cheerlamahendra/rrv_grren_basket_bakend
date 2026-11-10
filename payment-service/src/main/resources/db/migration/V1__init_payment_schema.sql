-- ==============================================================================
-- RRV GREEN BASKET — PAYMENT SERVICE SCHEMA
-- Migration: V1__init_payment_schema.sql
-- Database: payment_db
-- ==============================================================================

-- 1. Payments Table (Root Entity for Order Payment Lifecycles)
CREATE TABLE IF NOT EXISTS payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_public_id VARCHAR(36) NOT NULL UNIQUE,
    order_public_id VARCHAR(36) NOT NULL,
    customer_public_id VARCHAR(36) NOT NULL,
    payment_method VARCHAR(30) NOT NULL,
    payment_status VARCHAR(40) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    total_amount DECIMAL(12, 2) NOT NULL,
    advance_percentage INT NOT NULL DEFAULT 100,
    advance_amount DECIMAL(12, 2) NOT NULL,
    advance_paid_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    delivery_collection_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    delivery_collected_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    outstanding_amount DECIMAL(12, 2) NOT NULL,
    refunded_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_payment_total_amount CHECK (total_amount >= 0),
    CONSTRAINT chk_payment_advance_amount CHECK (advance_amount >= 0),
    CONSTRAINT chk_payment_outstanding CHECK (outstanding_amount >= 0),
    CONSTRAINT chk_payment_refunded CHECK (refunded_amount >= 0)
);

CREATE INDEX idx_payments_order ON payments (order_public_id);
CREATE INDEX idx_payments_customer ON payments (customer_public_id);
CREATE INDEX idx_payments_status ON payments (payment_status);
CREATE INDEX idx_payments_method ON payments (payment_method);
CREATE INDEX idx_payments_created_at ON payments (created_at);

-- 2. Payment Transactions Table (Individual Transaction Records: Full Online, Advance, Cash at Delivery, Refunds)
CREATE TABLE IF NOT EXISTS payment_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_public_id VARCHAR(36) NOT NULL UNIQUE,
    payment_id BIGINT NOT NULL,
    transaction_type VARCHAR(40) NOT NULL,
    transaction_status VARCHAR(30) NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    provider VARCHAR(30) NOT NULL DEFAULT 'RAZORPAY',
    provider_order_id VARCHAR(100) NULL,
    provider_payment_id VARCHAR(100) NULL,
    provider_reference VARCHAR(100) NULL,
    failure_code VARCHAR(100) NULL,
    failure_description TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_tx_payment FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE CASCADE
);

CREATE INDEX idx_tx_payment ON payment_transactions (payment_id);
CREATE INDEX idx_tx_provider_payment ON payment_transactions (provider_payment_id);
CREATE INDEX idx_tx_provider_order ON payment_transactions (provider_order_id);
CREATE INDEX idx_tx_status ON payment_transactions (transaction_status);
CREATE INDEX idx_tx_type ON payment_transactions (transaction_type);

-- 3. Payment Refunds Table (Online Refunds via Razorpay)
CREATE TABLE IF NOT EXISTS payment_refunds (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    refund_public_id VARCHAR(36) NOT NULL UNIQUE,
    payment_id BIGINT NOT NULL,
    provider_refund_id VARCHAR(100) NULL,
    refund_amount DECIMAL(12, 2) NOT NULL,
    refund_status VARCHAR(30) NOT NULL,
    refund_reason VARCHAR(255) NULL,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_refund_payment FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE CASCADE
);

CREATE INDEX idx_refund_payment ON payment_refunds (payment_id);
CREATE INDEX idx_refund_provider_id ON payment_refunds (provider_refund_id);
CREATE INDEX idx_refund_status ON payment_refunds (refund_status);

-- 4. Payment Webhook Events Table (Reliable Inbox Pattern with Deduplication)
CREATE TABLE IF NOT EXISTS payment_webhook_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider VARCHAR(30) NOT NULL DEFAULT 'RAZORPAY',
    provider_event_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(60) NOT NULL,
    event_payload LONGTEXT NOT NULL,
    processing_status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP NULL,
    error_message TEXT NULL,
    CONSTRAINT uq_webhook_provider_event UNIQUE (provider, provider_event_id)
);

CREATE INDEX idx_webhook_status ON payment_webhook_events (processing_status);
CREATE INDEX idx_webhook_received ON payment_webhook_events (received_at);
CREATE INDEX idx_webhook_type ON payment_webhook_events (event_type);

-- 5. COD Collections Table (Authorized Cash Collected by Delivery Personnel)
CREATE TABLE IF NOT EXISTS cod_collections (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    collection_public_id VARCHAR(36) NOT NULL UNIQUE,
    payment_id BIGINT NOT NULL,
    order_public_id VARCHAR(36) NOT NULL,
    collected_amount DECIMAL(12, 2) NOT NULL,
    collector_reference VARCHAR(100) NOT NULL,
    delivery_reference VARCHAR(100) NOT NULL,
    collection_status VARCHAR(30) NOT NULL DEFAULT 'COLLECTED',
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    collected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cod_payment FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE CASCADE
);

CREATE INDEX idx_cod_payment ON cod_collections (payment_id);
CREATE INDEX idx_cod_order ON cod_collections (order_public_id);
CREATE INDEX idx_cod_delivery_ref ON cod_collections (delivery_reference);
CREATE INDEX idx_cod_collector_ref ON cod_collections (collector_reference);

-- 6. Payment Status History Table (Audit Trail for Transitions)
CREATE TABLE IF NOT EXISTS payment_status_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id BIGINT NOT NULL,
    previous_status VARCHAR(40) NULL,
    new_status VARCHAR(40) NOT NULL,
    reason VARCHAR(255) NULL,
    changed_by VARCHAR(100) NOT NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_history_payment FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE CASCADE
);

CREATE INDEX idx_history_payment ON payment_status_history (payment_id);
CREATE INDEX idx_history_changed_at ON payment_status_history (changed_at);

-- 7. Payment Idempotency Keys Table (Preventing Duplicate Operations & Replay)
CREATE TABLE IF NOT EXISTS payment_idempotency_keys (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    operation_type VARCHAR(50) NOT NULL,
    resource_reference VARCHAR(100) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    response_reference VARCHAR(100) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_idempotency_type ON payment_idempotency_keys (operation_type);
CREATE INDEX idx_idempotency_expires ON payment_idempotency_keys (expires_at);
CREATE INDEX idx_idempotency_resource ON payment_idempotency_keys (resource_reference);

-- 8. Payment Event Outbox Table (Reliable Transactional Outbox for Order Service Events)
CREATE TABLE IF NOT EXISTS payment_event_outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_public_id VARCHAR(36) NOT NULL UNIQUE,
    aggregate_type VARCHAR(50) NOT NULL DEFAULT 'PAYMENT',
    aggregate_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(60) NOT NULL,
    event_payload LONGTEXT NOT NULL,
    publishing_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 5,
    last_error TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP NULL
);

CREATE INDEX idx_outbox_status_retry ON payment_event_outbox (publishing_status, retry_count);
CREATE INDEX idx_outbox_created_at ON payment_event_outbox (created_at);
CREATE INDEX idx_outbox_aggregate ON payment_event_outbox (aggregate_id);
