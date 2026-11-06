# RRV GREEN BASKET – CUSTOMER AUTHENTICATION & SESSION MANAGEMENT API CONTRACT

## 1. System Architecture & Topology

| Service | Port | Database | Primary Responsibility |
| :--- | :--- | :--- | :--- |
| **API Gateway** | `8080` | None | Edge routing, perimeter header stripping, HMAC assertion signing, negative session cache |
| **Auth Service** | `8081` | `auth_db` | Passwordless OTP lifecycle, JWT tokens, session lifecycle, onboarding saga |
| **Customer Service** | `8082` | `customer_db` | Customer profile management, delivery addresses, ownership authorization |
| **Config Server** | `8888` | Git/Native | Centralized Spring Cloud configuration |
| **Discovery Server** | `8761` | In-memory | Eureka service registry |

---

## 2. Canonical Identity & JWT Claims Model

### 2.1 Identity Binding Across Microservices
- Canonical Identifier: `auth_users.public_id` (UUID v4) $\equiv$ `customers.auth_user_id` (UUID v4).
- Internal database surrogate keys (`auth_users.id`, `customers.id`) are **never** exposed to clients.
- `public_id` is propagated consistently across all tokens and internal assertions.

### 2.2 JWT Claims Structure
```json
{
  "sub": "22c6a2aa-30d0-4103-ae32-03ce9c8fb66e",
  "public_id": "22c6a2aa-30d0-4103-ae32-03ce9c8fb66e",
  "userId": "22c6a2aa-30d0-4103-ae32-03ce9c8fb66e",
  "mobile_number": "+919876500001",
  "email": "mahendra@example.com",
  "role": "ROLE_CUSTOMER",
  "roles": ["ROLE_CUSTOMER"],
  "status": "ACTIVE",
  "sid": "f5187e9b-98bf-426a-838d-34f21b3fd27c",
  "jti": "a159c5c9-e97a-4484-8bdd-84ddfa24832f",
  "iss": "rrv-green-basket-auth",
  "iat": 1793426199,
  "exp": 1793427999
}
```
> [!IMPORTANT]
> **Claim Separation**: `mobile_number` strictly holds the customer's mobile number. `email` holds the customer's optional email or `null`. A mobile number is **never** placed into the `email` claim.

---

## 3. Customer Authentication APIs

All customer APIs are routed through the API Gateway on port `8080`.

### 3.1 Customer Registration – Send OTP
- **Route**: `POST /api/auth/customer/register/send-otp` (Public)
- **Request Body**:
```json
{
  "fullName": "Mahendra Kumar",
  "mobileNumber": "+919876500001",
  "email": "mahendra@example.com"
}
```
- **Validation**:
  - `fullName`: Required, 2-100 characters.
  - `mobileNumber`: Required, E.164 standard (`+91` followed by 10 digits starting with 6-9).
  - `email`: Optional, RFC 5322 compliant if provided.
  - Checks if mobile number is already registered $\rightarrow$ if registered, returns `409 Conflict`.
- **Response `200 OK`**:
```json
{
  "success": true,
  "message": "Registration OTP sent successfully",
  "data": {
    "mobileNumber": "+919876500001",
    "expiresInMinutes": 5,
    "resendCooldownSeconds": 60
  },
  "timestamp": "2026-10-31T11:30:00Z"
}
```

---

### 3.2 Customer Registration – Verify OTP & Automatic Login
- **Route**: `POST /api/auth/customer/register/verify-otp` (Public)
- **Request Body**:
```json
{
  "mobileNumber": "+919876500001",
  "otp": "482915"
}
```
- **Workflow**:
  1. Validates OTP hash and single-use challenge against `otp_challenges`.
  2. Stages `AuthUser` in `PENDING` status.
  3. Executes S2S call to `customer-service` with `Idempotency-Key` and S2S HMAC signature to initialize profile.
  4. On network timeout/error, performs state probe `GET /api/customers/internal/by-auth-user/{authUserId}`.
  5. Upon verified profile creation, promotes `AuthUser` to `ACTIVE`.
  6. Creates independent device session in `customer_sessions`.
  7. Issues access token and refresh token bound to the session.
- **Response `201 Created` (Auto-authenticated)**:
```json
{
  "success": true,
  "message": "Customer registered and authenticated successfully",
  "data": {
    "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
    "refreshToken": "bba4f0be-b25c-48e1-8ee2-c3ae566fa485-822b2347...",
    "tokenType": "Bearer",
    "expiresInSeconds": 1800,
    "user": {
      "publicId": "22c6a2aa-30d0-4103-ae32-03ce9c8fb66e",
      "mobileNumber": "+919876500001",
      "email": "mahendra@example.com",
      "role": "CUSTOMER",
      "status": "ACTIVE",
      "roles": ["ROLE_CUSTOMER"]
    }
  },
  "timestamp": "2026-10-31T11:30:05Z"
}
```

---

### 3.3 Returning Customer Login – Send OTP
- **Route**: `POST /api/auth/customer/login/send-otp` (Public)
- **Request Body**:
```json
{
  "mobileNumber": "+919876500001"
}
```
- **Response `200 OK`**:
```json
{
  "success": true,
  "message": "Login OTP sent successfully",
  "data": {
    "mobileNumber": "+919876500001",
    "expiresInMinutes": 5,
    "resendCooldownSeconds": 60
  },
  "timestamp": "2026-10-31T11:32:00Z"
}
```

---

### 3.4 Returning Customer Login – Verify OTP
- **Route**: `POST /api/auth/customer/login/verify-otp` (Public)
- **Request Body**:
```json
{
  "mobileNumber": "+919876500001",
  "otp": "719304"
}
```
- **Response `200 OK`**:
```json
{
  "success": true,
  "message": "Customer authenticated successfully",
  "data": {
    "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
    "refreshToken": "c9284fae-49b1-4bb2-a63e-...",
    "tokenType": "Bearer",
    "expiresInSeconds": 1800,
    "user": {
      "publicId": "22c6a2aa-30d0-4103-ae32-03ce9c8fb66e",
      "mobileNumber": "+919876500001",
      "email": "mahendra@example.com",
      "role": "CUSTOMER",
      "status": "ACTIVE",
      "roles": ["ROLE_CUSTOMER"]
    }
  },
  "timestamp": "2026-10-31T11:32:05Z"
}
```

---

### 3.5 Token Refresh & Rotation
- **Route**: `POST /api/auth/refresh` (Public)
- **Request Body**:
```json
{
  "refreshToken": "c9284fae-49b1-4bb2-a63e-..."
}
```
- **Security Invariant**:
  - Refresh tokens are single-use.
  - Upon presentation, a new refresh token and access token are issued.
  - If a previously used/revoked refresh token is re-submitted (replay attack), the entire token family and the underlying session are **immediately revoked**.

---

### 3.6 Single-Device Customer Logout & Immediate Revocation
- **Route**: `POST /api/auth/customer/logout` (Authenticated)
- **Headers**: `Authorization: Bearer <accessToken>`
- **Request Body**:
```json
{
  "refreshToken": "c9284fae-49b1-4bb2-a63e-..."
}
```
- **Guaranteed Behavior**:
  - The specific device session (`sid`) is marked `REVOKED` in `customer_sessions`.
  - The Gateway negative session cache receives the session ID immediately.
  - Any subsequent request presenting this access token is **immediately rejected** with `401 Unauthorized`.
  - Other device sessions for the same customer remain `ACTIVE`.
- **Response `200 OK`**:
```json
{
  "success": true,
  "message": "Logged out successfully from this device",
  "data": {
    "loggedOut": true
  },
  "timestamp": "2026-10-31T11:35:00Z"
}
```

---

## 4. Protected Customer Service APIs

### 4.1 Customer Profile (`/api/customers/me`)
- **Route**: `GET /api/customers/me`
- **Headers**: `Authorization: Bearer <accessToken>`
- **Authorization**: Required authority `ROLE_CUSTOMER`.
- **Response `200 OK`**:
```json
{
  "success": true,
  "message": "Customer profile retrieved successfully",
  "data": {
    "id": "7bc84564-816c-49ee-98f7-cba49f5d401f",
    "authUserId": "22c6a2aa-30d0-4103-ae32-03ce9c8fb66e",
    "fullName": "Mahendra Kumar",
    "mobileNumber": "+919876500001",
    "email": "mahendra@example.com",
    "status": "ACTIVE",
    "addresses": []
  },
  "timestamp": "2026-10-31T11:36:00Z"
}
```

### 4.2 Customer Address Management (`/api/customers/me/addresses`)
- **Create Address**: `POST /api/customers/me/addresses`
- **List Addresses**: `GET /api/customers/me/addresses`
- **Update Address**: `PUT /api/customers/me/addresses/{id}`
- **Delete Address**: `DELETE /api/customers/me/addresses/{id}`

---

## 5. Security Architecture Invariants

1. **Perimeter Security**:
   - `PerimeterSecurityFilter` at the API Gateway blocks external access to any `/internal/**` routes with `403 Forbidden`.
   - Strips client-supplied `X-User-*`, `X-Session-*`, and `X-Gateway-*` headers to eliminate identity spoofing.
2. **Gateway Assertion (`X-Gateway-Assertion`)**:
   - The Gateway validates the incoming JWT, verifies session validity with `auth-service`, and signs a short-lived (30s) JWT assertion forwarded to downstream microservices.
3. **Service-to-Service (S2S) Signing**:
   - Inter-service requests use canonical HMAC-SHA256 signatures with 30s timestamp window, SHA-256 body digest, and unique nonces stored in an in-memory sliding cache to prevent replay attacks.

---

## 6. Next.js Frontend Integration Guidelines

> [!NOTE]
> Backend contracts only. The frontend Next.js application should adhere to the following architecture when calling these endpoints.

### 6.1 Cookie & Token Storage Strategy
1. **Access Token**: Store in application memory or in a short-lived HTTP-only cookie (`SameSite=Strict`, `Secure=true`, `Path=/`).
2. **Refresh Token**: Store in an HTTP-only, secure cookie with `Path=/api/auth/refresh`.

### 6.2 Axios / Fetch Interceptor Pattern
```typescript
import axios from 'axios';

const api = axios.create({
  baseURL: process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080',
  withCredentials: true,
});

// Automatic token refresh interceptor
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;
      try {
        const refreshResponse = await axios.post(
          'http://localhost:8080/api/auth/refresh',
          { refreshToken: getStoredRefreshToken() },
          { withCredentials: true }
        );
        const { accessToken } = refreshResponse.data.data;
        originalRequest.headers.Authorization = `Bearer ${accessToken}`;
        return api(originalRequest);
      } catch (refreshError) {
        // Redirect to /login
        window.location.href = '/login';
        return Promise.reject(refreshError);
      }
    }
    return Promise.reject(error);
  }
);
```
