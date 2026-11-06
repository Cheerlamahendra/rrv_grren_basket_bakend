# RRV Green Basket — Customer Service Architectural Design

## 1. Architectural Overview

The **Customer Service** (`customer-service`) is an independent, domain-driven microservice in the **RRV Green Basket** e-commerce ecosystem owned by **AVM Smart Solutions**. It manages customer profile lifecycles, contact details, account standings, and multi-address delivery destinations for fresh produce orders.

```text
[ Next.js Frontend ]
        │
        ▼ (Port 8080)
[ Spring Cloud API Gateway ] (JWT Verification, Edge Rate Limiting, Header Injection)
        │
        ├─► [ Auth Service: 8081 ] (OTP Verification, BCrypt Login, Token Issuance)
        │
        └─► [ Customer Service: 8082 ] (Profile & Address Management, customer_db)
```

---

## 2. Responsibilities and Strict Boundaries

### What Customer Service Owns:
- Customer personal profile details (`fullName`, `email`, `mobileNumber`, `profileImageUrl`).
- Customer account standings (`ACTIVE`, `INACTIVE`, `SUSPENDED`).
- Customer delivery address management (home, work, other).
- Multi-address persistence with single-default address invariants.
- Scoped data access ensuring customers cannot view or mutate another user's addresses.

### What Customer Service Does NOT Own:
- Authentication credentials (passwords, PINs, OTP challenges) — exclusively handled by `auth-service`.
- Session lifecycle or token generation — handled by `auth-service`.
- Order placement, cart management, or payment processing — handled by downstream microservices (`cart-service`, `order-service`, `payment-service`).

---

## 3. Technology Stack & Multi-Module Integration

- **Java Version**: 21 (Microsoft OpenJDK 21 LTS)
- **Spring Boot**: 3.3.5
- **Spring Cloud**: 2023.0.3 (Eureka Client, Config Client)
- **Persistence**: Spring Data JPA 3.3.5, Hibernate 6.5, MySQL Connector/J 9.1.0
- **Database Migrations**: Flyway 10.15 (MySQL extension)
- **Security**: Spring Security 6.3, JJWT 0.12.6
- **Documentation**: SpringDoc OpenAPI 2.6.0 (Swagger UI at `/swagger-ui.html`)
- **Port**: `8082`
- **Database**: `customer_db` on MySQL port `3306`

---

## 4. Package Structure & Key Components

```text
com.rrvgreenbasket.customer
├── CustomerServiceApplication.java
├── config/
│   └── OpenApiConfig.java
├── controller/
│   ├── CustomerAddressController.java
│   └── CustomerProfileController.java
├── dto/
│   ├── request/
│   │   ├── AddressRequest.java
│   │   ├── CreateCustomerProfileRequest.java
│   │   ├── UpdateCustomerProfileRequest.java
│   │   └── UpdateCustomerStatusRequest.java
│   └── response/
│       ├── AddressResponse.java
│       ├── ApiResponse.java
│       └── CustomerResponse.java
├── entity/
│   ├── Customer.java
│   └── CustomerAddress.java
├── enums/
│   ├── AccountStatus.java
│   └── AddressType.java
├── exception/
│   ├── DuplicateResourceException.java
│   ├── ForbiddenOperationException.java
│   ├── GlobalExceptionHandler.java
│   ├── InvalidRequestException.java
│   └── ResourceNotFoundException.java
├── mapper/
│   ├── AddressMapper.java
│   └── CustomerMapper.java
├── repository/
│   ├── CustomerAddressRepository.java
│   └── CustomerRepository.java
├── security/
│   ├── CustomAccessDeniedHandler.java
│   ├── HeaderAuthenticationFilter.java
│   ├── JwtAuthenticationEntryPoint.java
│   ├── JwtTokenProvider.java
│   ├── SecurityConfig.java
│   └── UserPrincipal.java
└── service/
    ├── AddressService.java
    ├── CustomerService.java
    └── impl/
        ├── AddressServiceImpl.java
        └── CustomerServiceImpl.java
```

---

## 5. Security & Edge Authentication (Defense-in-Depth)

Customer Service implements a **hybrid dual-path security mechanism** via `HeaderAuthenticationFilter`:

1. **Gateway-Routed Path**:
   The API Gateway verifies the JWT token at the edge, checks role authorization, and forwards requests with enriched identity headers:
   - `X-User-Id`: Public UUID of authenticated user
   - `X-User-Email`: Email or mobile identifier
   - `X-User-Role`: Granted role (`ROLE_CUSTOMER`, `ROLE_ADMIN`, etc.)
   `HeaderAuthenticationFilter` reads these headers, constructs `UserPrincipal`, and populates Spring's `SecurityContext`.

2. **Direct Fallback Path**:
   When testing Customer Service directly on port `8082`, `HeaderAuthenticationFilter` inspects `Authorization: Bearer <token>`, validates the cryptographic HMAC-SHA signature using `JwtTokenProvider`, and extracts identity claims.

3. **Role Enforcement**:
   - Customer-scoped endpoints (`/api/customers/me`, `/api/customers/me/addresses/**`) require authentication.
   - Administrative endpoints (`GET /api/customers`, `PATCH /api/customers/{customerId}/status`) are guarded by `@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")`.

---

## 6. Core Business Invariants

1. **Profile Uniqueness**:
   - Exactly one customer profile is allowed per `authUserId`.
   - `mobileNumber` and `email` are unique across all customer records.
2. **Address Quota**:
   - Each customer is allowed a maximum of 10 delivery addresses to prevent resource exhaustion.
3. **Single Default Address**:
   - The first address created for a customer automatically becomes `isDefault = true`.
   - If an address is created or marked default, any existing default address for that customer is atomically set to `false`.
   - If a default address is deleted and other addresses remain, the system automatically promotes the newest remaining address to default.
4. **Tenant Isolation**:
   - Customers can only access or modify their own addresses. Cross-customer address mutations result in `404 Not Found` or `403 Forbidden`.
