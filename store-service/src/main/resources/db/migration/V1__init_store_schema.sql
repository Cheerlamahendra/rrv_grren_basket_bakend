-- ==============================================================================
-- RRV Green Basket — Store Service Database Migration V1
-- Target Database: store_db (MySQL 9 / H2 Test Compatibility)
-- ==============================================================================

-- 1. Table: store_settings
CREATE TABLE store_settings (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL,
    store_name VARCHAR(150) NOT NULL,
    brand_tagline VARCHAR(255) NULL,
    support_email VARCHAR(150) NOT NULL,
    support_phone VARCHAR(50) NOT NULL,
    physical_address VARCHAR(500) NOT NULL,
    website_url VARCHAR(255) NOT NULL,
    logo_file_name VARCHAR(255) NULL,
    logo_s3_key VARCHAR(500) NULL,
    logo_url VARCHAR(500) NULL,
    favicon_file_name VARCHAR(255) NULL,
    favicon_s3_key VARCHAR(500) NULL,
    favicon_url VARCHAR(500) NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(50) NULL,
    CONSTRAINT uk_store_settings_public_id UNIQUE (public_id)
);

CREATE INDEX idx_store_settings_active ON store_settings (is_active);

-- 2. Table: store_settings_audit
CREATE TABLE store_settings_audit (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    store_settings_public_id VARCHAR(36) NOT NULL,
    actor_id VARCHAR(50) NOT NULL,
    actor_role VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    changed_fields TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_store_audit_settings_id ON store_settings_audit (store_settings_public_id);
CREATE INDEX idx_store_audit_created_at ON store_settings_audit (created_at);

-- 3. Seed Initial Store Settings Record
INSERT INTO store_settings (
    public_id,
    store_name,
    brand_tagline,
    support_email,
    support_phone,
    physical_address,
    website_url,
    is_active,
    updated_by
) VALUES (
    '11111111-2222-3333-4444-555555555555',
    'RRV Green Basket',
    'Freshness Delivered Daily',
    'rrvgreenbasket@gmail.com',
    '+91 98765 43210',
    'Kurnool, Andhra Pradesh, India',
    'https://www.rrvgreenbasket.in',
    TRUE,
    'SYSTEM'
);

INSERT INTO store_settings_audit (
    store_settings_public_id,
    actor_id,
    actor_role,
    action,
    changed_fields
) VALUES (
    '11111111-2222-3333-4444-555555555555',
    'SYSTEM',
    'SYSTEM',
    'INITIALIZE',
    'Initial default settings seeded'
);
