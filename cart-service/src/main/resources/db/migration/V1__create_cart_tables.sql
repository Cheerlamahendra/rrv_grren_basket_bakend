
-- ============================================
-- RRV Green Basket - Cart Service
-- Migration: V1
-- Database: cart_db
-- ============================================

-- 1. CARTS TABLE
CREATE TABLE carts (
                       id BIGINT NOT NULL AUTO_INCREMENT,
                       public_id CHAR(36) NOT NULL,
                       customer_public_id CHAR(36) NOT NULL,

                       status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    -- Enforces one ACTIVE cart per customer
                       active_customer_public_id CHAR(36)
                           GENERATED ALWAYS AS (
                               CASE
                                   WHEN status = 'ACTIVE'
                                       THEN customer_public_id
                                   ELSE NULL
                                   END
                               ) STORED,

                       version BIGINT NOT NULL DEFAULT 0,

                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                           ON UPDATE CURRENT_TIMESTAMP,

                       PRIMARY KEY (id),

                       CONSTRAINT uk_carts_public_id
                           UNIQUE (public_id),

                       CONSTRAINT uk_carts_active_customer
                           UNIQUE (active_customer_public_id),

                       INDEX idx_carts_customer (customer_public_id),
                       INDEX idx_carts_status (status),

                       CONSTRAINT chk_carts_status
                           CHECK (status IN ('ACTIVE', 'CHECKED_OUT', 'ABANDONED'))
);


-- 2. CART ITEMS TABLE
CREATE TABLE cart_items (
                            id BIGINT NOT NULL AUTO_INCREMENT,
                            public_id CHAR(36) NOT NULL,
                            cart_id BIGINT NOT NULL,

                            product_public_id CHAR(36) NOT NULL,

                            product_name_snapshot VARCHAR(200) NOT NULL,
                            product_image_url_snapshot VARCHAR(1000),

                            quantity DECIMAL(12,3) NOT NULL,
                            unit_of_measure VARCHAR(20) NOT NULL,

                            price_per_kg_snapshot DECIMAL(12,2) NOT NULL,

                            version BIGINT NOT NULL DEFAULT 0,

                            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                                ON UPDATE CURRENT_TIMESTAMP,

                            PRIMARY KEY (id),

                            CONSTRAINT uk_cart_items_public_id
                                UNIQUE (public_id),

                            CONSTRAINT uk_cart_product_unit
                                UNIQUE (cart_id, product_public_id, unit_of_measure),

                            CONSTRAINT fk_cart_items_cart
                                FOREIGN KEY (cart_id)
                                    REFERENCES carts(id)
                                    ON DELETE CASCADE,

                            INDEX idx_cart_items_cart (cart_id),
                            INDEX idx_cart_items_product (product_public_id),

                            CONSTRAINT chk_cart_items_quantity
                                CHECK (quantity > 0),

                            CONSTRAINT chk_cart_items_price
                                CHECK (price_per_kg_snapshot >= 0)
);
