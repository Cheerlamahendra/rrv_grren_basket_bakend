# RRV Green Basket – Auth Service

> **Spring Boot 3.x Microservice** | Email OTP Registration · Email/Password Login · Forgot Password · Resend Email Integration

---

## Table of Contents

- [Overview](#overview)
- [Technology Stack](#technology-stack)
- [Architecture](#architecture)
- [New Features (V3)](#new-features-v3)
- [API Endpoints](#api-endpoints)
- [Environment Variables](#environment-variables)
- [Database Migrations](#database-migrations)
- [Running Locally](#running-locally)
- [Security Design](#security-design)

---

## Overview

The Auth Service is responsible for all authentication and credential management in the **RRV Green Basket** e-commerce platform. It handles:

- Customer registration (email OTP two-phase flow)
- Email + password login
- Forgot password (3-phase: OTP → reset token → new password)
- Admin login (BCrypt, unchanged)
- JWT access + refresh token issuance and rotation
- Multi-device session management
- Service-to-service (S2S) HMAC authentication

> **Architectural Rule**: Auth Service owns credentials and identity. Customer profile data (name, address, etc.) is owned and managed by **Customer Service**.

---

## Technology Stack

| Component | Version |
|---|---|
| Java | 17 |
| Spring Boot | 3.x |
| Spring Security | 6.x |
| Spring Data JPA / Hibernate | 6.x |
| MySQL | 8.0+ |
| Flyway | 10.x |
| JWT (JJWT) | 0.12.x |
| BCrypt | Spring Security built-in |
| Email Delivery | **Resend API** |
| Service Discovery | Eureka (Spring Cloud) |
| Config | Spring Cloud Config Server |
| API Docs | Springdoc OpenAPI / Swagger UI |

---

## Architecture

```
API Gateway ──► Auth Service ──► Customer Service (profile provisioning)
                    │
                    ├── auth_users (identity)
                    ├── customer_auth (credentials, email, password_hash)
                    ├── otp_challenges (OTP staging)
                    ├── password_reset_tokens (reset authorization)
                    ├── customer_sessions (multi-device)
                    └── refresh_tokens (JWT rotation)
```

### Registration Saga (Distributed Transaction)

```
Client ──► [send-otp] ──► OtpChallenge (PENDING, staged data)
       ──► [verify-otp] ──► AuthUser (PENDING) + CustomerAuth
                        ──► Customer Service: initializeCustomerProfile()
                        ──► [IF FAIL] Account stays PENDING, NO tokens issued
                        ──► [IF OK] AuthUser → ACTIVE, JWT tokens returned
```

---

## New Features (V3)

### V3 Database Migration (`V3__customer_email_password_auth.sql`)

| Change | Description |
|---|---|
| `customer_auth.email` | Unique normalized email (new primary login identifier) |
| `customer_auth.password_hash` | BCrypt hash of customer password |
| `customer_auth.email_verified` | Boolean flag set after OTP verification |
| `customer_auth.email_verified_at` | Timestamp of first OTP verification |
| `otp_challenges.pending_mobile_number` | Staged mobile during registration |
| `otp_challenges.pending_password_hash` | Staged BCrypt password hash during registration |
| `otp_challenges.mobile_number` | Now nullable (email-only challenges) |
| `password_reset_tokens` | New table for single-use reset authorization tokens |
| `OtpPurpose.PASSWORD_RESET` | New enum value for reset OTP challenges |

---

## API Endpoints

### Base URL: `POST /api/auth/customer/`

#### Email Registration – Phase 1: Send OTP

```
POST /api/auth/customer/email-register/send-otp
```

```json
{
  "fullName": "Mahendra Kumar",
  "mobileNumber": "9876543210",
  "email": "mahendra@example.com",
  "password": "StrongPassword@123"
}
```

**Response `200`:**
```json
{
  "success": true,
  "message": "A verification OTP has been sent to your email address. Valid for 5 minute(s).",
  "data": { "email": "mahendra@example.com", "expiresInMinutes": 5, "cooldownSeconds": 60 }
}
```

---

#### Email Registration – Phase 2: Verify OTP

```
POST /api/auth/customer/email-register/verify-otp
```

```json
{ "email": "mahendra@example.com", "otp": "482910" }
```

**Response `201`:** Full `AuthResponse` with `accessToken`, `refreshToken`, `user`.

> ⚠️ Returns `503` if Customer Service profile creation fails. Account stays in PENDING state.

---

#### Email + Password Login

```
POST /api/auth/customer/login
```

```json
{ "email": "mahendra@example.com", "password": "StrongPassword@123" }
```

**Response `200`:** `AuthResponse` with tokens.

---

#### Forgot Password – Phase 1: Send OTP

```
POST /api/auth/customer/forgot-password/send-otp
```

```json
{ "email": "mahendra@example.com" }
```

> Returns consistent response regardless of whether email exists (anti-enumeration).

---

#### Forgot Password – Phase 2: Verify OTP

```
POST /api/auth/customer/forgot-password/verify-otp
```

```json
{ "email": "mahendra@example.com", "otp": "193847" }
```

**Response `200`:**
```json
{
  "resetToken": "uuid-uuid",
  "expiresInMinutes": 15,
  "message": "OTP verified. Use the reset token to set your new password within 15 minutes."
}
```

---

#### Forgot Password – Phase 3: Reset Password

```
POST /api/auth/customer/forgot-password/reset
```

```json
{
  "resetToken": "uuid-uuid",
  "newPassword": "NewStrongPassword@123",
  "confirmPassword": "NewStrongPassword@123"
}
```

**Response `200`:** Password reset confirmation. All existing sessions revoked.

---

#### Token Refresh

```
POST /api/auth/customer/refresh
```
```json
{ "refreshToken": "..." }
```

---

#### Customer Logout

```
POST /api/auth/customer/logout
Authorization: Bearer <accessToken>
```
```json
{ "refreshToken": "..." }
```

---

### Admin Login (unchanged)

```
POST /api/auth/admin/login
```
```json
{ "email": "admin@rrvgreenbasket.com", "password": "AdminPassword@123" }
```

---

## Environment Variables

Copy [`.env.example`](.env.example) to `.env` and fill in your values:

| Variable | Description | Required |
|---|---|---|
| `MYSQL_USER` | MySQL username | ✅ |
| `MYSQL_PASSWORD` | MySQL password | ✅ |
| `JWT_SECRET` | Min 32-char JWT signing secret | ✅ |
| `RESEND_API_KEY` | Resend API key (`re_...`) | ✅ |
| `RESEND_FROM_EMAIL` | Verified sender email in Resend | ✅ |
| `INTERNAL_SERVICE_SECRET` | HMAC S2S secret (shared with other services) | ✅ |
| `CUSTOMER_SERVICE_URL` | URL of Customer Service | ✅ |
| `TEST_ADMIN_PASSWORD` | Seed admin password (dev only) | Dev only |

> **Never commit `.env` to version control.**

### Getting a Resend API Key

1. Sign up at [resend.com](https://resend.com)
2. Go to **API Keys** → **Create API Key**
3. Verify your sender domain under **Domains**
4. Set `RESEND_API_KEY=re_your_key` and `RESEND_FROM_EMAIL=noreply@yourdomain.com`

---

## Database Migrations

Flyway runs automatically on startup. Migrations are in:
```
src/main/resources/db/migration/
├── V1__init_auth_schema.sql
├── V2__admin_auth.sql
└── V3__customer_email_password_auth.sql   ← New in this release
```

> If the MySQL instance already has V1/V2 applied, V3 runs automatically on next startup.

---

## Running Locally

### Prerequisites

- Java 17+
- MySQL 8.0+
- Discovery Server running on port 8761
- Config Server running on port 8888 (or set `spring.cloud.config.fail-fast=false`)
- Customer Service running on port 8082

### Steps

```bash
# 1. Create the database
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS auth_db CHARACTER SET utf8mb4;"

# 2. Set environment variables
export RESEND_API_KEY=re_your_key_here
export RESEND_FROM_EMAIL=noreply@yourdomain.com
export JWT_SECRET=your_32_char_secret_here
export MYSQL_USER=root
export MYSQL_PASSWORD=your_password

# 3. Run
./mvnw spring-boot:run
```

Swagger UI: **http://localhost:8081/swagger-ui.html**

---

## Security Design

| Feature | Implementation |
|---|---|
| OTP hashing | BCrypt (same encoder as passwords) |
| Password hashing | BCrypt strength 12 |
| OTP staging | Stored in `otp_challenges` with TTL |
| Reset token | UUID opaque token; only SHA-256 hash stored |
| Anti-enumeration | Forgot-password always returns consistent response |
| S2S auth | HMAC-SHA256 request signatures |
| Session revocation | MySQL-backed; zero lag |
| Token rotation | Refresh token family rotation |
| OTP rate limiting | Cooldown + max attempts enforced per email |
| No plaintext secrets | API keys read from env vars; never logged |
| OTP never logged | `rawOtp` passed only to email provider, never to logger |
