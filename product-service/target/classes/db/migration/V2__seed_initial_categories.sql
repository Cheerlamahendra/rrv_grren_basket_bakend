-- ==============================================================================
-- Migration: V2__seed_initial_categories.sql
-- Description: Seed exactly six initial main product categories (idempotent)
-- ==============================================================================

INSERT INTO product_categories (public_id, name, slug, description, image_url, sort_order, is_active, created_at, updated_at)
SELECT 'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c01', 'Fruits', 'fruits', 'Fresh and seasonal farm-fresh fruits.', NULL, 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM product_categories WHERE slug = 'fruits');

INSERT INTO product_categories (public_id, name, slug, description, image_url, sort_order, is_active, created_at, updated_at)
SELECT 'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c02', 'Leafy Vegetables', 'leafy-vegetables', 'Nutrient-rich fresh green leafy vegetables.', NULL, 2, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM product_categories WHERE slug = 'leafy-vegetables');

INSERT INTO product_categories (public_id, name, slug, description, image_url, sort_order, is_active, created_at, updated_at)
SELECT 'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c03', 'Root Vegetables', 'root-vegetables', 'Fresh underground harvest including potatoes, onions, carrots, and beets.', NULL, 3, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM product_categories WHERE slug = 'root-vegetables');

INSERT INTO product_categories (public_id, name, slug, description, image_url, sort_order, is_active, created_at, updated_at)
SELECT 'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c04', 'Seasonings', 'seasonings', 'Fresh herbs, chillies, ginger, garlic, and aromatic natural seasonings.', NULL, 4, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM product_categories WHERE slug = 'seasonings');

INSERT INTO product_categories (public_id, name, slug, description, image_url, sort_order, is_active, created_at, updated_at)
SELECT 'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c05', 'Mushrooms', 'mushrooms', 'Freshly cultivated white button and gourmet mushrooms.', NULL, 5, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM product_categories WHERE slug = 'mushrooms');

INSERT INTO product_categories (public_id, name, slug, description, image_url, sort_order, is_active, created_at, updated_at)
SELECT 'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c06', 'Other Items', 'other-items', 'Miscellaneous grocery, specialty crops, and auxiliary fresh produce.', NULL, 6, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM product_categories WHERE slug = 'other-items');
