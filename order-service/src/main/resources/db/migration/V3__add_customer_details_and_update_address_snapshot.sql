-- ==============================================================================
-- Flyway Migration V3: Add Registered Customer Snapshots to Orders Table
-- ==============================================================================

ALTER TABLE orders
    ADD COLUMN registered_customer_name VARCHAR(150) NULL AFTER customer_public_id,
    ADD COLUMN registered_customer_mobile VARCHAR(20) NULL AFTER registered_customer_name;

CREATE INDEX idx_orders_customer_mobile ON orders(registered_customer_mobile);
CREATE INDEX idx_orders_customer_name ON orders(registered_customer_name);
