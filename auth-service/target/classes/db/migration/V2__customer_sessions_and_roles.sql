-- ==============================================================================
-- Flyway Database Migration: V2__customer_sessions_and_roles.sql
-- Database: auth_db
-- Description: Customer Sessions, Metadata Tracking, and Verified Role Migration
-- ==============================================================================

-- 1. Modify CHECK constraint on auth_users to support 'CUSTOMER' alongside 'USER' and 'ADMIN'
ALTER TABLE `auth_users` DROP CHECK `chk_auth_users_role`;
ALTER TABLE `auth_users` ADD CONSTRAINT `chk_auth_users_role` CHECK ((`role` in ('USER','ADMIN','CUSTOMER')));

-- 2. Selectively migrate only verified customer accounts from legacy 'USER' to 'CUSTOMER'
UPDATE `auth_users`
SET `role` = 'CUSTOMER'
WHERE `role` = 'USER'
  AND `id` IN (
      SELECT `user_id` 
      FROM `customer_auth` 
      WHERE `mobile_verified` = 1
  );

-- 3. Add registration metadata columns to otp_challenges to preserve full_name and email
ALTER TABLE `otp_challenges`
  ADD COLUMN `full_name` varchar(150) DEFAULT NULL AFTER `requested_ip`,
  ADD COLUMN `email` varchar(254) DEFAULT NULL AFTER `full_name`;

-- 4. Create customer_sessions table for independent multi-device session management & immediate revocation
CREATE TABLE IF NOT EXISTS `customer_sessions` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `session_id` char(36) NOT NULL,
  `user_id` bigint unsigned NOT NULL,
  `device_info` varchar(255) DEFAULT NULL,
  `ip_address` varchar(45) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `last_active_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `revoked_at` datetime(6) DEFAULT NULL,
  `revocation_reason` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_sessions_session_id` (`session_id`),
  KEY `idx_customer_sessions_user_status` (`user_id`, `status`),
  KEY `idx_customer_sessions_status` (`status`),
  CONSTRAINT `fk_customer_sessions_user` FOREIGN KEY (`user_id`) REFERENCES `auth_users` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_customer_sessions_status` CHECK ((`status` in ('ACTIVE', 'REVOKED', 'EXPIRED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 5. Add session_id to auth_audit_events for session-level auditing
ALTER TABLE `auth_audit_events`
  ADD COLUMN `session_id` char(36) DEFAULT NULL AFTER `ip_address`,
  ADD KEY `idx_auth_audit_session` (`session_id`);
