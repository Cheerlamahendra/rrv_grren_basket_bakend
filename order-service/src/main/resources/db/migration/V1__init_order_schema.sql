-- ==============================================================================
-- Flyway Migration V1: Initial Order Service Schema
-- ==============================================================================

CREATE TABLE IF NOT EXISTS orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id CHAR(36) NOT NULL UNIQUE,
    order_number VARCHAR(30) NOT NULL UNIQUE,
    customer_public_id CHAR(36) NOT NULL,
    delivery_zone_public_id CHAR(36) NOT NULL,
    delivery_zone_name_snapshot VARCHAR(100) NOT NULL,
    order_status VARCHAR(30) NOT NULL,
    payment_status VARCHAR(30) NOT NULL,
    payment_method VARCHAR(30) NOT NULL,
    subtotal DECIMAL(12, 2) NOT NULL,
    discount_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    delivery_charge DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    total_amount DECIMAL(12, 2) NOT NULL,
    minimum_order_amount_snapshot DECIMAL(12, 2) NOT NULL,
    delivery_address_snapshot TEXT NOT NULL,
    delivery_latitude DECIMAL(10, 7) NOT NULL,
    delivery_longitude DECIMAL(10, 7) NOT NULL,
    promised_delivery_date DATE NOT NULL,
    order_placed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    cancelled_at DATETIME NULL,
    cancellation_reason VARCHAR(500) NULL,
    idempotency_key VARCHAR(100) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_orders_subtotal CHECK (subtotal >= 0),
    CONSTRAINT chk_orders_total CHECK (total_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_orders_customer ON orders(customer_public_id);
CREATE INDEX idx_orders_status ON orders(order_status);
CREATE INDEX idx_orders_number ON orders(order_number);
CREATE INDEX idx_orders_placed_at ON orders(order_placed_at);
CREATE INDEX idx_orders_delivery_date ON orders(promised_delivery_date);
CREATE INDEX idx_orders_idempotency ON orders(customer_public_id, idempotency_key);

CREATE TABLE IF NOT EXISTS order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id CHAR(36) NOT NULL UNIQUE,
    order_id BIGINT NOT NULL,
    product_public_id CHAR(36) NOT NULL,
    product_name_snapshot VARCHAR(200) NOT NULL,
    product_image_snapshot VARCHAR(1000) NULL,
    unit_snapshot VARCHAR(20) NOT NULL,
    quantity DECIMAL(12, 3) NOT NULL,
    unit_price DECIMAL(12, 2) NOT NULL,
    total_price DECIMAL(12, 2) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT chk_order_items_qty CHECK (quantity > 0),
    CONSTRAINT chk_order_items_price CHECK (unit_price >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_order_items_product ON order_items(product_public_id);

CREATE TABLE IF NOT EXISTS order_status_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    previous_status VARCHAR(30) NULL,
    new_status VARCHAR(30) NOT NULL,
    changed_by VARCHAR(50) NOT NULL,
    reason VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_status_history_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_status_history_order_id ON order_status_history(order_id);

CREATE TABLE IF NOT EXISTS order_business_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id CHAR(36) NOT NULL UNIQUE,
    order_acceptance_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    opening_time TIME NOT NULL DEFAULT '08:00:00',
    closing_time TIME NOT NULL DEFAULT '21:00:00',
    delivery_type VARCHAR(30) NOT NULL DEFAULT 'NEXT_DAY',
    delivery_days_offset INT NOT NULL DEFAULT 1,
    timezone VARCHAR(50) NOT NULL DEFAULT 'Asia/Kolkata',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by VARCHAR(50) NULL,
    version BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Initial Seed for Order Business Settings (8:00 AM to 9:00 PM cutoff, Next-Day Delivery, Asia/Kolkata)
INSERT INTO order_business_settings (
    public_id,
    order_acceptance_enabled,
    opening_time,
    closing_time,
    delivery_type,
    delivery_days_offset,
    timezone,
    created_at,
    updated_at,
    updated_by,
    version
) VALUES (
    'b4c5d6e7-f8a9-4b0c-1d2e-3f4a5b6c7d8e',
    TRUE,
    '08:00:00',
    '21:00:00',
    'NEXT_DAY',
    1,
    'Asia/Kolkata',
    NOW(),
    NOW(),
    'SYSTEM_INIT',
    0
);
