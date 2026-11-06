# Auth Service Verification & Database Schema Alignment Report

**Service Name:** Auth Service (`com.rrvgreenbasket.auth`)  
**Port:** `8081`  
**Database Schema:** MySQL 8/9 (`auth_db`)  
**Hibernate DDL Mode:** `validate` (Strict Production Schema Validation)  
**Status:** **100% VERIFIED & PRODUCTION READY**  

---

## 1. Executive Summary

The Auth Service has been completely audited, refactored, and verified against the existing 6-table MySQL database (`auth_db`). The previous schema mismatch (legacy 5-table role-based design vs intended 6-table relational design) has been completely resolved without dropping, altering, recreating, or losing any existing tables or rows.

Strict Hibernate validation (`spring.jpa.hibernate.ddl-auto=validate`) passed cleanly with zero warnings or errors. All unit and integration test suites executed with **5/5 tests passing (0 failures, 0 errors)**. The service is currently live and actively serving requests on port `8081`.

---

## 2. MySQL `auth_db` Intended Schema & Entity Mappings

All Java entities in package `com.rrvgreenbasket.auth.entity` match the physical MySQL database schema exactly:

| Table Name | Entity Class | Primary Key | Key Foreign Keys & Unique Constraints | Functional Responsibility |
|---|---|---|---|---|
| `auth_users` | `AuthUser` | `id` (BIGINT UNSIGNED) | `uk_auth_users_public_id` (`char(36)` UNIQUE)<br>`chk_auth_users_role` ('USER','ADMIN')<br>`chk_auth_users_status` ('PENDING','ACTIVE','BLOCKED','DISABLED') | Central user identity, UUID public identity, system role, account status |
| `customer_auth` | `CustomerAuth` | `user_id` (BIGINT UNSIGNED) | `fk_customer_auth_user` -> `auth_users(id)`<br>`uk_customer_auth_mobile` (`varchar(15)` UNIQUE) | Passwordless customer mobile credentials, verification status & timestamps |
| `admin_auth` | `AdminAuth` | `user_id` (BIGINT UNSIGNED) | `fk_admin_auth_user` -> `auth_users(id)`<br>`uk_admin_auth_email` (`varchar(254)` UNIQUE) | Administrator credentials, BCrypt password hash, failed attempt counter, lockout duration |
| `otp_challenges` | `OtpChallenge` | `id` (BIGINT UNSIGNED) | `idx_otp_mobile_purpose`<br>`chk_otp_purpose` ('REGISTRATION','LOGIN','MOBILE_CHANGE')<br>`chk_otp_attempts` (attempts <= max_attempts) | OTP lifecycle, BCrypt hashed OTP storage, 60s cooldown, attempt limits |
| `refresh_tokens` | `RefreshToken` | `id` (BIGINT UNSIGNED) | `uk_refresh_tokens_token_hash` (`char(64)` UNIQUE)<br>`fk_refresh_tokens_user` -> `auth_users(id)`<br>`fk_refresh_tokens_replaced_by` -> `refresh_tokens(id)` | Cryptographically hashed refresh tokens, token family tracking, replacement lineage |
| `auth_audit_events` | `AuthAuditEvent` | `id` (BIGINT UNSIGNED) | `fk_auth_audit_events_user` -> `auth_users(id)`<br>`idx_auth_audit_user_event` | Immutable audit log of all security events, logins, token rotations, lockouts, client IPs |

---

## 3. Flyway Migration Status

- **Database History Table:** `flyway_schema_history` in `auth_db`
- **Installed Rank:** `1`
- **Baseline Version:** `1` (`<< Flyway Baseline >>`)
- **Status:** `SUCCESS`
- **Migration Script Synchronized:** [`V1__init_auth_schema.sql`](file:///e:/avmsmart%20clients%20projects/RRV_GREEN_BASKET/avm-ecommerce-backend/auth-service/src/main/resources/db/migration/V1__init_auth_schema.sql) now reflects the exact 6-table DDL, preserving existing baseline integrity with `baseline-on-migrate: true`.
- **Schema Modifications:** No destructive DDL commands (`DROP`, `ALTER`, `TRUNCATE`) were executed.

---

## 4. Key Architectural Fixes Applied

1. **Strict Hibernate Schema Validation (`char` vs `varchar`):**
   - Corrected `public_id`, `token_hash`, and `token_family_id` with explicit `@Column(columnDefinition = "char(...)")` to match MySQL metadata precisely.
2. **Eliminated `LazyInitializationException`:**
   - Added `JOIN FETCH` queries to `AdminAuthRepository`, `CustomerAuthRepository`, `RefreshTokenRepository`, and `AuthUserRepository`.
   - Pre-extracted and decoupled security principal properties in `CustomUserDetails` so Spring Security never triggers uninitialized proxy fetches outside transactional boundaries.
3. **Transaction Boundary Alignment for Audit Logging:**
   - Normalized `AuthAuditServiceImpl.logEvent` to use standard `@Transactional` (`Propagation.REQUIRED`), allowing newly persisted entities and their corresponding audit logs to commit atomically without violating database foreign key constraints.
4. **Independent OTP Attempt Tracking:**
   - Configured `OtpServiceImpl.verifyOtp` with `@Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = {InvalidOtpException.class, OtpExpiredException.class})` to ensure failed OTP attempt increments commit immediately even if caller operations roll back.
5. **Replay Attack Token Family Invalidation:**
   - Configured `JwtTokenServiceImpl.rotateRefreshToken` and `AuthServiceImpl.refreshToken` with `noRollbackFor = TokenValidationException.class` so that when a revoked token is reused, all tokens in the compromised family are irrevocably revoked and persisted in MySQL.

---

## 5. Live Verification & Testing Results

### Maven Test Suite Execution
- **Command:** `mvn test package -pl auth-service`
- **Test Set:** `com.rrvgreenbasket.auth.AuthServiceApplicationTests`
- **Result:** **5 tests executed, 0 failures, 0 errors, 0 skipped.**
  - `testAdminAuthenticationAndLockout`: PASSED (BCrypt verification, attempt counter increments, 5th attempt triggers HTTP 423 Locked).
  - `testTokenRotationAndRevocation`: PASSED (Single-use token rotation, replay attack blocked, entire family invalidated, logout blacklist verified).
  - `testCustomerRegistrationAndLogin`: PASSED (Mobile OTP issuance, customer entity persistence, profile retrieval, login token exchange).
  - `testOtpDispatchAndCooldown`: PASSED (OTP generation, 60-second cooldown throttling returns HTTP 429).
  - `testInvalidOtpValidation`: PASSED (BCrypt OTP mismatch detection, attempt count tracking, max attempt enforcement).

### Live Daemon Verification on Port 8081
- **Actuator Health Probe (`GET http://localhost:8081/actuator/health`):**
  - Status: `UP`
  - MySQL Database: `UP` (`validationQuery=isValid()`)
- **Admin Login (`POST /api/auth/admin/login`):**
  - Returned HTTP `200 OK`, `ROLE_ADMIN` claim, issued access & refresh tokens.
  - Inserted event into `auth_audit_events`: `ADMIN_LOGIN_SUCCESS`.
- **Token Rotation (`POST /api/auth/refresh`):**
  - Old token revoked, new token issued within same `token_family_id`.
  - Lineage established with `replaced_by_token_id`.
- **Replay Attack Defense:**
  - Resubmitting old token immediately returned HTTP `401 Unauthorized`.
  - Entire family invalidated (`revoked_at` populated for all sibling tokens in family).
  - Inserted event into `auth_audit_events`: `TOKEN_REUSE_DETECTED`.
- **Customer Mobile OTP Workflow:**
  - `POST /api/auth/customer/send-otp` -> HTTP 200, challenge created in `otp_challenges`, event logged in `auth_audit_events`.
  - Immediate resend -> HTTP 429 (Cooldown active).
  - Invalid OTP -> HTTP 400, attempts incremented to 1, event logged: `OTP_MISMATCH`.
  - Expired OTP -> HTTP 400, marked `used_at`, event logged: `OTP_EXPIRED`.

---

## 6. Endpoints & Postman Testing Reference

The Postman collection has been updated and saved to:  
[`documentation/postman/Auth_Service_Postman_Collection.json`](file:///e:/avmsmart%20clients%20projects/RRV_GREEN_BASKET/avm-ecommerce-backend/documentation/postman/Auth_Service_Postman_Collection.json)

### Available Endpoints
1. `POST /api/auth/customer/send-otp` — Request 6-digit OTP for registration or login.
2. `POST /api/auth/customer/verify-otp` — Verify OTP code without creating session.
3. `POST /api/auth/customer/register` — Register customer account with verified OTP.
4. `POST /api/auth/customer/login` — Authenticate returning customer with OTP.
5. `POST /api/auth/admin/login` — Administrator email + BCrypt password authentication.
6. `POST /api/auth/refresh` — Rotate refresh token for a new access/refresh pair.
7. `GET /api/auth/me` — Inspect authenticated user profile (Bearer token required).
8. `POST /api/auth/logout` — Revoke refresh token & blacklist access token.
9. `GET /actuator/health` — Application and MySQL database health probe.
10. `GET /v3/api-docs` & `GET /swagger-ui.html` — OpenAPI v3 specification & Swagger UI.

---

## 7. Next Steps

Auth Service is fully verified, operational, and aligned with all 6 intended MySQL database tables.  
**Standing rule enforced:** Do not proceed to Customer Service until explicit user approval is granted.

