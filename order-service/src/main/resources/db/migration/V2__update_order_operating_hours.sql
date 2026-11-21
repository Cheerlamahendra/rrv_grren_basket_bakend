-- ==============================================================================
-- Flyway Migration V2: Update Store Operating Hours to 06:00 - 22:00
-- ==============================================================================

UPDATE order_business_settings
SET opening_time = '06:00:00',
    closing_time = '22:00:00',
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'MIGRATION_V2'
WHERE public_id = 'b4c5d6e7-f8a9-4b0c-1d2e-3f4a5b6c7d8e';
