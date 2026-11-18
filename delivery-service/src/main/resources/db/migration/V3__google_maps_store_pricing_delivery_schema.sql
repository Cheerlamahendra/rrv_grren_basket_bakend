-- ==============================================================================
-- Flyway Migration V3: Google Maps Store Location, Delivery Pricing Slabs & Customer Delivery Addresses
-- ==============================================================================

-- 1. Store Locations table for single/multi-store Google Maps road-distance delivery model
CREATE TABLE IF NOT EXISTS store_locations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id CHAR(36) NOT NULL UNIQUE,
    store_name VARCHAR(150) NOT NULL,
    google_place_id VARCHAR(255) NULL,
    formatted_address VARCHAR(500) NOT NULL,
    latitude DOUBLE NOT NULL,
    longitude DOUBLE NOT NULL,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    country VARCHAR(100) NOT NULL DEFAULT 'India',
    maximum_delivery_distance_meters INT NOT NULL DEFAULT 5000,
    delivery_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    primary_store BOOLEAN NOT NULL DEFAULT TRUE,
    minimum_order_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    free_delivery_threshold DECIMAL(10, 2) NULL,
    estimated_delivery_time VARCHAR(50) NOT NULL DEFAULT '30-45 mins',
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NULL,
    updated_by VARCHAR(100) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_store_max_dist CHECK (maximum_delivery_distance_meters > 0),
    CONSTRAINT chk_store_min_order CHECK (minimum_order_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_store_locations_primary ON store_locations(primary_store, delivery_enabled);
CREATE INDEX idx_store_locations_public_id ON store_locations(public_id);

-- 2. Delivery Pricing Slabs table for dynamic road-distance-based pricing
CREATE TABLE IF NOT EXISTS delivery_pricing_slabs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    minimum_distance_meters INT NOT NULL,
    maximum_distance_meters INT NOT NULL,
    delivery_charge DECIMAL(10, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    effective_start_date DATETIME NULL,
    effective_end_date DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NULL,
    updated_by VARCHAR(100) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_slab_dist CHECK (minimum_distance_meters >= 0 AND maximum_distance_meters > minimum_distance_meters),
    CONSTRAINT chk_slab_charge CHECK (delivery_charge >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_delivery_pricing_slabs_active ON delivery_pricing_slabs(active);
CREATE INDEX idx_delivery_pricing_slabs_distance ON delivery_pricing_slabs(minimum_distance_meters, maximum_distance_meters);

-- 3. Customer Delivery Addresses with Google Place ID and verified coordinates
CREATE TABLE IF NOT EXISTS customer_delivery_addresses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id CHAR(36) NOT NULL UNIQUE,
    customer_id VARCHAR(100) NOT NULL,
    google_place_id VARCHAR(255) NOT NULL,
    formatted_address VARCHAR(500) NOT NULL,
    address_line VARCHAR(255) NULL,
    landmark VARCHAR(150) NULL,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100) NOT NULL,
    postal_code VARCHAR(20) NULL,
    country VARCHAR(100) NOT NULL DEFAULT 'India',
    latitude DOUBLE NOT NULL,
    longitude DOUBLE NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    verified BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_cust_addr_customer_id ON customer_delivery_addresses(customer_id);
CREATE INDEX idx_cust_addr_public_id ON customer_delivery_addresses(public_id);
CREATE INDEX idx_cust_addr_default ON customer_delivery_addresses(customer_id, is_default);

-- 4. Initial seed data: Primary Store in Kurnool, Andhra Pradesh
INSERT INTO store_locations (
    public_id, store_name, google_place_id, formatted_address,
    latitude, longitude, city, state, postal_code, country,
    maximum_delivery_distance_meters, delivery_enabled, primary_store,
    minimum_order_amount, free_delivery_threshold, estimated_delivery_time, currency,
    created_by, updated_by
) VALUES (
    'store-kurnool-primary', 'RRV Green Basket - Kurnool Central', 'ChIJ7eXy0rP_yDsRzZqj9Y1Lw6s',
    'Near Collector Office, Kurnool, Andhra Pradesh 518001, India',
    15.8281000, 78.0373000, 'Kurnool', 'Andhra Pradesh', '518001', 'India',
    5000, TRUE, TRUE,
    0.00, 500.00, '30-45 mins', 'INR',
    'SYSTEM', 'SYSTEM'
);

-- 5. Initial seed data: Dynamic Distance-Based Delivery Pricing Slabs (up to 5 km)
INSERT INTO delivery_pricing_slabs (
    minimum_distance_meters, maximum_distance_meters, delivery_charge, currency, active, created_by, updated_by
) VALUES
(0, 1000, 10.00, 'INR', TRUE, 'SYSTEM', 'SYSTEM'),
(1001, 2000, 15.00, 'INR', TRUE, 'SYSTEM', 'SYSTEM'),
(2001, 3000, 20.00, 'INR', TRUE, 'SYSTEM', 'SYSTEM'),
(3001, 4000, 25.00, 'INR', TRUE, 'SYSTEM', 'SYSTEM'),
(4001, 5000, 30.00, 'INR', TRUE, 'SYSTEM', 'SYSTEM');
