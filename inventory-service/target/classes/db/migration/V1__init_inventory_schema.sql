-- ==============================================================================
-- RRV GREEN BASKET — INVENTORY SERVICE SCHEMA
-- Migration: V1__init_inventory_schema.sql
-- Database: inventory_db
-- ==============================================================================

-- 1. Inventory Items Table (Central Single-Warehouse Stock Ledger)
CREATE TABLE IF NOT EXISTS inventory_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL UNIQUE,
    product_public_id VARCHAR(36) NOT NULL UNIQUE,
    unit_of_measure VARCHAR(20) NOT NULL,
    physical_quantity DECIMAL(12, 3) NOT NULL DEFAULT 0.000,
    reserved_quantity DECIMAL(12, 3) NOT NULL DEFAULT 0.000,
    available_quantity DECIMAL(12, 3) NOT NULL DEFAULT 0.000,
    low_stock_threshold DECIMAL(12, 3) NOT NULL DEFAULT 10.000,
    initialized BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_physical_quantity_non_negative CHECK (physical_quantity >= 0),
    CONSTRAINT chk_reserved_quantity_non_negative CHECK (reserved_quantity >= 0),
    CONSTRAINT chk_available_quantity_non_negative CHECK (available_quantity >= 0),
    CONSTRAINT chk_reserved_not_exceed_physical CHECK (reserved_quantity <= physical_quantity),
    INDEX idx_inventory_items_product (product_public_id),
    INDEX idx_inventory_items_unit (unit_of_measure),
    INDEX idx_inventory_items_available (available_quantity),
    INDEX idx_inventory_items_initialized (initialized)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Stock Movements Table (Immutable Audit Ledger with Exact Before/After Semantics)
CREATE TABLE IF NOT EXISTS stock_movements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL UNIQUE,
    product_public_id VARCHAR(36) NOT NULL,
    movement_type VARCHAR(40) NOT NULL,
    quantity DECIMAL(12, 3) NOT NULL,
    physical_quantity_before DECIMAL(12, 3) NOT NULL,
    physical_quantity_after DECIMAL(12, 3) NOT NULL,
    available_quantity_before DECIMAL(12, 3) NOT NULL,
    available_quantity_after DECIMAL(12, 3) NOT NULL,
    unit_of_measure VARCHAR(20) NOT NULL,
    reason VARCHAR(100) NOT NULL,
    reference_type VARCHAR(50) NULL,
    reference_id VARCHAR(100) NULL,
    notes VARCHAR(500) NULL,
    performed_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_movement_type CHECK (movement_type IN (
        'INITIAL_STOCK', 'STOCK_IN', 'STOCK_OUT', 'ADJUSTMENT', 
        'RESERVATION_DEDUCTION', 'OTHER_APPROVED_MOVEMENT'
    )),
    INDEX idx_movements_product (product_public_id),
    INDEX idx_movements_type (movement_type),
    INDEX idx_movements_created_at (created_at),
    INDEX idx_movements_reference (reference_type, reference_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Stock Reservation Requests (Parent Table for Multi-Product Idempotent Reservations)
CREATE TABLE IF NOT EXISTS stock_reservation_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL UNIQUE,
    order_public_id VARCHAR(36) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_reservation_req_status CHECK (status IN ('ACTIVE', 'CONFIRMED', 'RELEASED', 'EXPIRED')),
    INDEX idx_res_requests_order (order_public_id),
    INDEX idx_res_requests_status (status),
    INDEX idx_res_requests_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Stock Reservation Items (Child Table for Line-Item Reservations)
CREATE TABLE IF NOT EXISTS stock_reservation_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL UNIQUE,
    reservation_request_id BIGINT NOT NULL,
    order_public_id VARCHAR(36) NOT NULL,
    product_public_id VARCHAR(36) NOT NULL,
    reserved_quantity DECIMAL(12, 3) NOT NULL,
    unit_of_measure VARCHAR(20) NOT NULL,
    item_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_res_items_request FOREIGN KEY (reservation_request_id) REFERENCES stock_reservation_requests (id) ON DELETE CASCADE,
    CONSTRAINT chk_res_item_qty_positive CHECK (reserved_quantity > 0),
    CONSTRAINT chk_res_item_status CHECK (item_status IN ('ACTIVE', 'CONFIRMED', 'RELEASED', 'EXPIRED')),
    INDEX idx_res_items_request (reservation_request_id),
    INDEX idx_res_items_product (product_public_id),
    INDEX idx_res_items_order (order_public_id),
    INDEX idx_res_items_status (item_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Stock Alerts Table (Automated Threshold & Stockout Monitoring)
CREATE TABLE IF NOT EXISTS stock_alerts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL UNIQUE,
    product_public_id VARCHAR(36) NOT NULL,
    alert_type VARCHAR(30) NOT NULL,
    current_quantity DECIMAL(12, 3) NOT NULL,
    threshold_quantity DECIMAL(12, 3) NOT NULL,
    unit_of_measure VARCHAR(20) NOT NULL,
    alert_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    unresolved_marker VARCHAR(70) GENERATED ALWAYS AS (
        CASE WHEN alert_status IN ('ACTIVE', 'ACKNOWLEDGED') THEN CONCAT(product_public_id, ':', alert_type) ELSE NULL END
    ),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP NULL,
    CONSTRAINT chk_alert_type CHECK (alert_type IN ('LOW_STOCK', 'OUT_OF_STOCK')),
    CONSTRAINT chk_alert_status CHECK (alert_status IN ('ACTIVE', 'ACKNOWLEDGED', 'RESOLVED')),
    CONSTRAINT uq_stock_alerts_unresolved UNIQUE (unresolved_marker),
    INDEX idx_alerts_product_status (product_public_id, alert_type, alert_status),
    INDEX idx_alerts_status (alert_status),
    INDEX idx_alerts_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Inventory Event Outbox Table (Reliable Transactional Messaging with Delivery Acknowledgment)
CREATE TABLE IF NOT EXISTS inventory_event_outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL UNIQUE,
    product_public_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(60) NOT NULL,
    payload JSON NOT NULL,
    event_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 5,
    last_error TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP NULL,
    CONSTRAINT chk_event_status CHECK (event_status IN ('PENDING', 'IN_PROGRESS', 'PUBLISHED', 'FAILED')),
    INDEX idx_outbox_status_retry (event_status, retry_count),
    INDEX idx_outbox_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
