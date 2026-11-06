# RRV Green Basket — Customer Service API Documentation

All Customer Service APIs are accessed externally through the **API Gateway** on port `8080`:
`http://localhost:8080/api/customers/...`

Or directly during local microservice development on port `8082`:
`http://localhost:8082/api/customers/...`

---

## 1. Authentication Headers

All non-public endpoints require an **Authorization Header**:
```http
Authorization: Bearer <JWT_ACCESS_TOKEN>
Content-Type: application/json
```

When routed through the API Gateway, the Gateway verifies the JWT and automatically injects:
```http
X-User-Id: <AUTH_USER_UUID>
X-User-Email: <EMAIL_OR_MOBILE>
X-User-Role: ROLE_CUSTOMER | ROLE_ADMIN
```

---

## 2. Standard API Response Structure

All successful responses follow the unified envelope:
```json
{
  "success": true,
  "message": "Operation completed successfully",
  "data": { ... },
  "timestamp": "2026-10-31T08:00:00.000Z"
}
```

Standard error envelope:
```json
{
  "success": false,
  "message": "Error description message",
  "data": null,
  "timestamp": "2026-10-31T08:00:00.000Z"
}
```

---

## 3. Customer Profile Endpoints

### 3.1 Create Customer Profile
- **Method**: `POST`
- **Path**: `/api/customers`
- **Description**: Initializes a new customer profile. Binds to the authenticated user from token context.
- **Request Body**:
```json
{
  "fullName": "Suresh Raina",
  "mobileNumber": "9876543210",
  "email": "suresh.raina@example.com",
  "profileImageUrl": "https://cdn.rrvgreenbasket.com/profiles/suresh.jpg"
}
```
- **Response** (`201 Created`):
```json
{
  "success": true,
  "message": "Customer profile created successfully",
  "data": {
    "id": "35a398db-687b-4fa5-a6a5-25f1c59023ef",
    "authUserId": "afd635e6-d879-4a37-92e4-fd11fce7a111",
    "fullName": "Suresh Raina",
    "email": "suresh.raina@example.com",
    "mobileNumber": "9876543210",
    "profileImageUrl": "https://cdn.rrvgreenbasket.com/profiles/suresh.jpg",
    "status": "ACTIVE",
    "createdAt": "2026-10-31T08:15:00.123456",
    "updatedAt": "2026-10-31T08:15:00.123456",
    "addresses": []
  },
  "timestamp": "2026-10-31T08:15:00.200Z"
}
```

---

### 3.2 Get Current Customer Profile
- **Method**: `GET`
- **Path**: `/api/customers/me`
- **Description**: Retrieves the complete profile and delivery address list of the logged-in customer.
- **Response** (`200 OK`):
```json
{
  "success": true,
  "message": "Customer profile retrieved successfully",
  "data": {
    "id": "35a398db-687b-4fa5-a6a5-25f1c59023ef",
    "authUserId": "afd635e6-d879-4a37-92e4-fd11fce7a111",
    "fullName": "Suresh Raina",
    "email": "suresh.raina@example.com",
    "mobileNumber": "9876543210",
    "profileImageUrl": "https://cdn.rrvgreenbasket.com/profiles/suresh.jpg",
    "status": "ACTIVE",
    "createdAt": "2026-10-31T08:15:00.123456",
    "updatedAt": "2026-10-31T08:15:00.123456",
    "addresses": [
      {
        "id": "687ea025-45fd-46d5-a904-777ba3bc25a0",
        "customerId": "35a398db-687b-4fa5-a6a5-25f1c59023ef",
        "addressType": "HOME",
        "receiverName": "Suresh Raina",
        "receiverMobile": "9876543210",
        "addressLine1": "Villa 14, Royal Greens",
        "addressLine2": "Phase 2, Outer Ring Road",
        "landmark": "Opposite D-Mart",
        "city": "Bangalore",
        "state": "Karnataka",
        "pincode": "560103",
        "country": "India",
        "createdAt": "2026-10-31T08:16:00.123456",
        "updatedAt": "2026-10-31T08:16:00.123456",
        "isDefault": true
      }
    ]
  },
  "timestamp": "2026-10-31T08:17:00.000Z"
}
```

---

### 3.3 Update Current Customer Profile
- **Method**: `PUT`
- **Path**: `/api/customers/me`
- **Description**: Updates full name, email, or profile image for the logged-in customer.
- **Request Body**:
```json
{
  "fullName": "Suresh Kumar Raina",
  "email": "suresh.k.raina@example.com",
  "profileImageUrl": "https://cdn.rrvgreenbasket.com/profiles/suresh-new.jpg"
}
```
- **Response** (`200 OK`):
```json
{
  "success": true,
  "message": "Customer profile updated successfully",
  "data": {
    "id": "35a398db-687b-4fa5-a6a5-25f1c59023ef",
    "fullName": "Suresh Kumar Raina",
    "email": "suresh.k.raina@example.com",
    "mobileNumber": "9876543210",
    "status": "ACTIVE",
    "updatedAt": "2026-10-31T08:18:22.000"
  },
  "timestamp": "2026-10-31T08:18:22.050Z"
}
```

---

### 3.4 Get Customer Profile by ID
- **Method**: `GET`
- **Path**: `/api/customers/{customerId}`
- **Description**: Retrieves customer profile by public UUID. Accessible by profile owner or admin.

---

### 3.5 Update Customer Profile by ID
- **Method**: `PUT`
- **Path**: `/api/customers/{customerId}`
- **Description**: Updates customer profile by public UUID. Accessible by owner or admin.

---

### 3.6 Search / List Customers (Admin)
- **Method**: `GET`
- **Path**: `/api/customers?query=Raina&status=ACTIVE&page=0&size=20`
- **Permissions**: `ROLE_ADMIN`, `ROLE_SUPER_ADMIN`
- **Query Parameters**:
  - `query` (optional): search keyword across name, email, or mobile
  - `status` (optional): `ACTIVE`, `INACTIVE`, `SUSPENDED`
  - `page` (default 0), `size` (default 20), `sortBy` (default `createdAt`), `sortDirection` (default `DESC`)
- **Response** (`200 OK`): Paginated customer objects.

---

### 3.7 Update Customer Account Status (Admin)
- **Method**: `PATCH`
- **Path**: `/api/customers/{customerId}/status`
- **Permissions**: `ROLE_ADMIN`, `ROLE_SUPER_ADMIN`
- **Request Body**:
```json
{
  "status": "SUSPENDED"
}
```
- **Response** (`200 OK`): Updated customer profile with new status.

---

## 4. Delivery Address Endpoints

### 4.1 List All Addresses
- **Method**: `GET`
- **Path**: `/api/customers/me/addresses`
- **Description**: Returns all saved delivery addresses for the logged-in customer, ordered with default address first.
- **Response** (`200 OK`): Array of address objects.

---

### 4.2 Add New Delivery Address
- **Method**: `POST`
- **Path**: `/api/customers/me/addresses`
- **Description**: Adds a delivery address. If it's the customer's first address, it automatically becomes default. If `isDefault: true` is passed, existing default addresses are reset atomically.
- **Request Body**:
```json
{
  "receiverName": "Suresh Raina",
  "receiverMobile": "9876543210",
  "addressLine1": "Tower 4, Flat 502, Prestige Park",
  "addressLine2": "Marathahalli - Sarjapur Road",
  "landmark": "Near Total Mall",
  "city": "Bangalore",
  "state": "Karnataka",
  "pincode": "560103",
  "country": "India",
  "addressType": "HOME",
  "isDefault": true
}
```
- **Response** (`201 Created`): Created address object with `isDefault: true`.

---

### 4.3 Get Address by ID
- **Method**: `GET`
- **Path**: `/api/customers/me/addresses/{addressId}` (or `/api/customers/addresses/{addressId}`)
- **Description**: Retrieves single address by public UUID.
- **Response** (`200 OK`): Single address object.

---

### 4.4 Update Delivery Address
- **Method**: `PUT`
- **Path**: `/api/customers/me/addresses/{addressId}` (or `/api/customers/addresses/{addressId}`)
- **Description**: Updates address attributes.
- **Request Body**: Full address payload with updated fields.
- **Response** (`200 OK`): Updated address object.

---

### 4.5 Set Address as Default
- **Method**: `PATCH`
- **Path**: `/api/customers/me/addresses/{addressId}/default` (or `/api/customers/addresses/{addressId}/default`)
- **Description**: Designates this address as the default delivery destination and resets all other addresses for this customer to non-default.
- **Response** (`200 OK`): Address object with `isDefault: true`.

---

### 4.6 Delete Delivery Address
- **Method**: `DELETE`
- **Path**: `/api/customers/me/addresses/{addressId}` (or `/api/customers/addresses/{addressId}`)
- **Description**: Deletes the specified address. If a default address is deleted, the newest remaining address is automatically promoted to default.
- **Response** (`200 OK`):
```json
{
  "success": true,
  "message": "Address deleted successfully",
  "timestamp": "2026-10-31T08:20:00.000Z"
}
```
