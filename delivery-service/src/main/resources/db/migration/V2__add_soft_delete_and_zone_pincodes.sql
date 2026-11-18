-- ==============================================================================
-- Flyway Migration V2: Add Soft Delete Support & Delivery Zone Multi-PIN Mapping
-- ==============================================================================

-- 1. Add soft delete tracking columns to delivery_zones
ALTER TABLE delivery_zones
    ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE AFTER active,
    ADD COLUMN deleted_at DATETIME NULL AFTER deleted;

CREATE INDEX idx_delivery_zones_deleted ON delivery_zones(deleted);
CREATE INDEX idx_delivery_zones_city_active_deleted ON delivery_zones(city, state, active, deleted);

-- 2. Create normalized table for mapping multiple PIN codes to a delivery zone
CREATE TABLE IF NOT EXISTS delivery_zone_pincodes (
    zone_id BIGINT NOT NULL,
    pincode VARCHAR(10) NOT NULL,
    CONSTRAINT pk_delivery_zone_pincodes PRIMARY KEY (zone_id, pincode),
    CONSTRAINT fk_delivery_zone_pincodes_zone FOREIGN KEY (zone_id) REFERENCES delivery_zones(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_delivery_zone_pincodes_pincode ON delivery_zone_pincodes(pincode);

-- 3. Backfill existing single pincodes into normalized pincode mapping table
INSERT IGNORE INTO delivery_zone_pincodes (zone_id, pincode)
SELECT id, TRIM(pincode)
FROM delivery_zones
WHERE pincode IS NOT NULL AND TRIM(pincode) <> '';
