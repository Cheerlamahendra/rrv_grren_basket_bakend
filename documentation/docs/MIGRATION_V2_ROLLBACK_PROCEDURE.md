# RRV GREEN BASKET – V2 MIGRATION MANUAL DISASTER RECOVERY & ROLLBACK SOP

> [!WARNING]
> **CRITICAL OPERATIONAL NOTICE**: Flyway Community Edition does **NOT** support automatic downward rollback (`flyway:undo` is commercial-only). Under **NO CIRCUMSTANCES** should destructive rollback SQL be automated or executed without an explicit database backup and senior DBA sign-off. This document specifies the emergency disaster recovery procedure.

---

## 1. Scope and Prerequisites

This procedure applies to `auth_db` running schema version `V2__customer_sessions_and_roles.sql`.

Before executing any rollback or manual schema intervention:
1. **Execute Full Cold or Logical Database Backup**:
   ```bash
   mysqldump -u root -p --single-transaction --routines --triggers auth_db > auth_db_pre_rollback_$(date +%Y%m%d_%H%M%S).sql
   ```
2. **Stop Traffic to Microservices**:
   - Stop `auth-service` (Port 8081).
   - Stop `customer-service` (Port 8082).
   - Drain or disable Gateway routing to `/api/auth/**`.

---

## 2. Dry-Run Inspection & Pre-Rollback Audit

Run the following audit SQL in MySQL Workbench or CLI to evaluate the impact of rollback:

```sql
USE auth_db;

-- 1. Inspect sessions created under V2
SELECT status, count(*) AS total_sessions 
FROM customer_sessions 
GROUP BY status;

-- 2. Inspect active OTP challenges containing full_name and email metadata
SELECT count(*) AS metadata_challenges 
FROM otp_challenges 
WHERE full_name IS NOT NULL OR email IS NOT NULL;

-- 3. Inspect customer accounts created under V2
SELECT count(*) AS new_v2_customers 
FROM auth_users 
WHERE role = 'CUSTOMER';

-- 4. Verify flyway schema history state
SELECT installed_rank, version, description, type, script, checksum, installed_on, execution_time, success 
FROM flyway_schema_history 
ORDER BY installed_rank DESC;
```

---

## 3. Manual Rollback SQL (Disaster Recovery Only)

If and only if an unrecoverable bug in V2 requires returning to the exact V1 schema, execute the following statements sequentially:

```sql
USE auth_db;

START TRANSACTION;

-- Step 1: Drop foreign keys and customer_sessions table
DROP TABLE IF EXISTS customer_sessions;

-- Step 2: Remove session_id index from auth_audit_events
ALTER TABLE auth_audit_events DROP INDEX idx_auth_audit_session;
ALTER TABLE auth_audit_events DROP COLUMN session_id;

-- Step 3: Remove staged metadata columns from otp_challenges
ALTER TABLE otp_challenges DROP COLUMN email;
ALTER TABLE otp_challenges DROP COLUMN full_name;

-- Step 4: Revert any CUSTOMER roles back to USER
UPDATE auth_users 
SET role = 'USER', updated_at = NOW() 
WHERE role = 'CUSTOMER';

-- Step 5: Restore CHECK constraint to ('USER', 'ADMIN')
ALTER TABLE auth_users DROP CONSTRAINT chk_auth_users_role;
ALTER TABLE auth_users ADD CONSTRAINT chk_auth_users_role 
    CHECK (role IN ('USER', 'ADMIN'));

-- Step 6: Remove the V2 migration record from flyway_schema_history so Flyway can re-run or align
DELETE FROM flyway_schema_history WHERE version = '2';

COMMIT;
```

---

## 4. Verification After Rollback

Verify the restored database structure:
```sql
USE auth_db;

-- Confirm tables match V1 baseline
SHOW TABLES;
-- Expected: admin_auth, auth_audit_events, auth_users, customer_auth, flyway_schema_history, otp_challenges, refresh_tokens

-- Confirm customer_sessions does not exist
SELECT COUNT(*) FROM information_schema.tables 
WHERE table_schema = 'auth_db' AND table_name = 'customer_sessions';
-- Expected: 0

-- Confirm columns removed from otp_challenges
DESCRIBE otp_challenges;
-- Expected: id, mobile_number, otp_hash, purpose, attempts, max_attempts, expires_at, created_at, verified_at

-- Verify Flyway schema history
SELECT version, description, success FROM flyway_schema_history;
-- Expected: Only version 1 present with success = 1
```

---

## 5. Service Recovery

1. Restart services in startup order:
   - `discovery-server` (8761)
   - `config-server` (8888)
   - `auth-service` (8081)
   - `customer-service` (8082)
   - `api-gateway` (8080)
2. Monitor log files for clean startup and validation of baseline authentication.
