-- ==============================================================================
-- Flyway Migration V1: Initial Delivery Service Schema
-- ==============================================================================

CREATE TABLE IF NOT EXISTS delivery_zones (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id CHAR(36) NOT NULL UNIQUE,
    zone_name VARCHAR(100) NOT NULL,
    zone_code VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(500) NULL,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100) NOT NULL,
    pincode VARCHAR(10) NULL,
    boundary_geojson LONGTEXT NOT NULL,
    delivery_charge DECIMAL(10, 2) NOT NULL,
    minimum_order_amount DECIMAL(10, 2) NOT NULL,
    free_delivery_threshold DECIMAL(10, 2) NULL,
    estimated_delivery_time VARCHAR(50) NOT NULL DEFAULT 'NEXT_DAY',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(50) NULL,
    updated_by VARCHAR(50) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_delivery_charge CHECK (delivery_charge >= 0),
    CONSTRAINT chk_min_order_amount CHECK (minimum_order_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_delivery_zones_active ON delivery_zones(active);
CREATE INDEX idx_delivery_zones_city ON delivery_zones(city, state);
CREATE INDEX idx_delivery_zones_code ON delivery_zones(zone_code);
