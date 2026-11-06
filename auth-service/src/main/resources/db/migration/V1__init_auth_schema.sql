-- ==============================================================================
-- Flyway Database Migration: V1__init_auth_schema.sql
-- Database: auth_db
-- Description: Core Authentication & Authorization Schema (6 Intended Tables)
-- ==============================================================================

-- 1. Central Auth Users Table
CREATE TABLE IF NOT EXISTS `auth_users` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `public_id` char(36) NOT NULL,
  `role` varchar(20) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auth_users_public_id` (`public_id`),
  CONSTRAINT `chk_auth_users_role` CHECK ((`role` in ('USER','ADMIN'))),
  CONSTRAINT `chk_auth_users_status` CHECK ((`status` in ('PENDING','ACTIVE','BLOCKED','DISABLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2. Customer Auth Extension Table
CREATE TABLE IF NOT EXISTS `customer_auth` (
  `user_id` bigint unsigned NOT NULL,
  `mobile_number` varchar(15) NOT NULL,
  `mobile_verified` tinyint(1) NOT NULL DEFAULT '0',
  `mobile_verified_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_customer_auth_mobile` (`mobile_number`),
  CONSTRAINT `fk_customer_auth_user` FOREIGN KEY (`user_id`) REFERENCES `auth_users` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 3. Admin Auth Extension Table
CREATE TABLE IF NOT EXISTS `admin_auth` (
  `user_id` bigint unsigned NOT NULL,
  `email` varchar(254) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `failed_login_attempts` int unsigned NOT NULL DEFAULT '0',
  `locked_until` datetime(6) DEFAULT NULL,
  `password_changed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_admin_auth_email` (`email`),
  CONSTRAINT `fk_admin_auth_user` FOREIGN KEY (`user_id`) REFERENCES `auth_users` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 4. OTP Challenges Table
CREATE TABLE IF NOT EXISTS `otp_challenges` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `mobile_number` varchar(15) NOT NULL,
  `purpose` varchar(30) NOT NULL,
  `otp_hash` varchar(255) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `attempts` int unsigned NOT NULL DEFAULT '0',
  `max_attempts` int unsigned NOT NULL DEFAULT '5',
  `used_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `requested_ip` varchar(45) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_otp_mobile_purpose` (`mobile_number`,`purpose`),
  KEY `idx_otp_expires_at` (`expires_at`),
  CONSTRAINT `chk_otp_attempts` CHECK ((`attempts` <= `max_attempts`)),
  CONSTRAINT `chk_otp_purpose` CHECK ((`purpose` in ('REGISTRATION','LOGIN','MOBILE_CHANGE')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 5. Refresh Tokens Table (Rotation & Family Tracking)
CREATE TABLE IF NOT EXISTS `refresh_tokens` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL,
  `token_hash` char(64) NOT NULL,
  `token_family_id` char(36) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `revoked_at` datetime(6) DEFAULT NULL,
  `replaced_by_token_id` bigint unsigned DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refresh_tokens_token_hash` (`token_hash`),
  KEY `idx_refresh_tokens_user_id` (`user_id`),
  KEY `idx_refresh_tokens_family_id` (`token_family_id`),
  KEY `fk_refresh_tokens_replaced_by` (`replaced_by_token_id`),
  CONSTRAINT `fk_refresh_tokens_replaced_by` FOREIGN KEY (`replaced_by_token_id`) REFERENCES `refresh_tokens` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_refresh_tokens_user` FOREIGN KEY (`user_id`) REFERENCES `auth_users` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 6. Auth Audit Events Table
CREATE TABLE IF NOT EXISTS `auth_audit_events` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned DEFAULT NULL,
  `event_type` varchar(50) NOT NULL,
  `success` tinyint(1) NOT NULL,
  `ip_address` varchar(45) DEFAULT NULL,
  `details` varchar(500) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_auth_audit_user_created` (`user_id`,`created_at`),
  KEY `idx_auth_audit_event_created` (`event_type`,`created_at`),
  CONSTRAINT `fk_auth_audit_user` FOREIGN KEY (`user_id`) REFERENCES `auth_users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
