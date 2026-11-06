-- ==============================================================================
-- Flyway Database Migration: V1__init_customer_schema.sql
-- Database: customer_db
-- Description: Customer Profile & Address Management Schema
-- ==============================================================================

-- 1. Customers Table (Profile & Contact Information)
CREATE TABLE IF NOT EXISTS `customers` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `public_id` char(36) NOT NULL,
  `auth_user_id` char(36) NOT NULL,
  `full_name` varchar(150) NOT NULL,
  `email` varchar(254) DEFAULT NULL,
  `mobile_number` varchar(20) NOT NULL,
  `profile_image_url` varchar(500) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customers_public_id` (`public_id`),
  UNIQUE KEY `uk_customers_auth_user_id` (`auth_user_id`),
  UNIQUE KEY `uk_customers_mobile` (`mobile_number`),
  KEY `idx_customers_email` (`email`),
  KEY `idx_customers_status` (`status`),
  CONSTRAINT `chk_customers_status` CHECK ((`status` in ('ACTIVE','INACTIVE','SUSPENDED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2. Customer Delivery Addresses Table
CREATE TABLE IF NOT EXISTS `customer_addresses` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `public_id` char(36) NOT NULL,
  `customer_id` bigint unsigned NOT NULL,
  `address_type` varchar(20) NOT NULL DEFAULT 'HOME',
  `receiver_name` varchar(150) NOT NULL,
  `receiver_mobile` varchar(20) NOT NULL,
  `address_line1` varchar(255) NOT NULL,
  `address_line2` varchar(255) DEFAULT NULL,
  `landmark` varchar(150) DEFAULT NULL,
  `city` varchar(100) NOT NULL,
  `state` varchar(100) NOT NULL,
  `pincode` varchar(20) NOT NULL,
  `country` varchar(100) NOT NULL DEFAULT 'India',
  `is_default` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_addresses_public_id` (`public_id`),
  KEY `idx_customer_addresses_customer_id` (`customer_id`),
  KEY `idx_customer_addresses_default` (`customer_id`, `is_default`),
  CONSTRAINT `fk_customer_addresses_customer` FOREIGN KEY (`customer_id`) REFERENCES `customers` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_customer_address_type` CHECK ((`address_type` in ('HOME','WORK','OTHER')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
