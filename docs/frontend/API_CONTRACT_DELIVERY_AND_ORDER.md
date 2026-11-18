# RRV Green Basket — Delivery Service & Order Service Frontend API Contract

This document provides complete, authoritative API contracts for the **Next.js Frontend Developer** integrating with the Delivery Service and Order Service via the Spring Cloud API Gateway (`http://localhost:8080`).

---

## 1. Architecture & Gateway Authentication Overview

All frontend requests route through **API Gateway** (`http://localhost:8080`).
- **Base URL**: `http://localhost:8080`
- **Customer Authentication**: Header `Authorization: Bearer <customer_jwt>`
- **Admin Authentication**: Header `Authorization: Bearer <admin_jwt>`
- **Public Endpoints**: No authorization header required.
- **Envelope Standard**: All endpoints return standard `ApiResponse<T>`:
```json
{
  "success": true,
  "message": "Human-readable status or error message",
  "data": { ... },
  "timestamp": "2026-11-10T12:00:00Z"
}
```

---

## 2. Delivery Service APIs

### 2.1 Get Active Delivery Zones (Public / Customer)
- **Method**: `GET`
- **Gateway URL**: `/api/delivery/zones`
- **Auth**: None (Public)
- **Headers**: `Accept: application/json`
- **Success Response (200 OK)**:
```json
{
  "success": true,
  "message": "Active delivery zones fetched successfully",
  "data": [
    {
      "publicId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "zoneName": "Kurnool Central Zone",
      "city": "Kurnool",
      "state": "Andhra Pradesh",
      "deliveryCharge": 30.00,
      "minimumOrderAmount": 199.00,
      "freeDeliveryThreshold": 499.00,
      "estimatedDeliveryTime": "Next morning 7:00 AM - 10:00 AM",
      "description": "Covers core Kurnool town and adjacent sectors"
    }
  ],
  "timestamp": "2026-11-10T08:30:00Z"
}
```

---

### 2.2 Check Delivery Availability (Public / Customer)
- **Method**: `POST`
- **Gateway URL**: `/api/delivery/check-availability`
- **Auth**: None (Public)
- **Headers**: `Content-Type: application/json`
- **Request Body**:
```json
{
  "latitude": 15.8281,
  "longitude": 78.0373,
  "address": "Gandhi Nagar, Kurnool"
}
```
- **Validation Rules**:
  - `latitude`: Required, double between `-90.0` and `90.0`
  - `longitude`: Required, double between `-180.0` and `180.0`
  - `address`: Optional string description
- **Success Response (200 OK — Available)**:
```json
{
  "success": true,
  "message": "Delivery is available in your selected area",
  "data": {
    "available": true,
    "zonePublicId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "zoneName": "Kurnool Central Zone",
    "deliveryCharge": 30.00,
    "minimumOrderAmount": 199.00,
    "freeDeliveryThreshold": 499.00,
    "estimatedDeliveryTime": "Next morning 7:00 AM - 10:00 AM",
    "message": "Delivery is available in your selected area"
  },
  "timestamp": "2026-11-10T08:31:00Z"
}
```
- **Success Response (200 OK — Outside Service Area)**:
```json
{
  "success": true,
  "message": "We currently do not deliver to this location",
  "data": {
    "available": false,
    "zonePublicId": null,
    "zoneName": null,
    "deliveryCharge": null,
    "minimumOrderAmount": null,
    "freeDeliveryThreshold": null,
    "estimatedDeliveryTime": null,
    "message": "We currently do not deliver to this location"
  },
  "timestamp": "2026-11-10T08:31:00Z"
}
```

---

### 2.3 Customer Address Selection & Validation Workflow
When the customer picks an address or pins on Google Maps in Next.js:
1. Obtain `latitude` and `longitude` from Google Maps Places Autocomplete / Geocoding API.
2. Call `POST /api/delivery/check-availability`.
3. If `available === false`, display a warning modal: *"Sorry! We do not deliver to this location yet."* and disable the "Proceed to Checkout" button.
4. If `available === true`, display:
   - Base delivery charge (e.g. ₹30)
   - Free delivery banner (e.g. *"Add ₹150 more to get FREE delivery!"*)
   - Minimum order requirement (e.g. ₹199 minimum order)

---

### 2.4 Admin Create Delivery Zone
- **Method**: `POST`
- **Gateway URL**: `/api/admin/delivery/zones`
- **Auth**: `ROLE_ADMIN` or `ROLE_SUPER_ADMIN`
- **Headers**: `Content-Type: application/json`
- **Request Body**:
```json
{
  "zoneName": "Kurnool North Zone",
  "city": "Kurnool",
  "state": "Andhra Pradesh",
  "coordinates": [
    [78.0300, 15.8200],
    [78.0400, 15.8200],
    [78.0400, 15.8300],
    [78.0300, 15.8300],
    [78.0300, 15.8200]
  ],
  "deliveryCharge": 35.00,
  "minimumOrderAmount": 200.00,
  "freeDeliveryThreshold": 500.00,
  "estimatedDeliveryTime": "Next morning 7:00 AM - 10:00 AM",
  "active": true,
  "description": "Covers northern residential sectors"
}
```
- **Coordinate Rules**:
  - Minimum 4 points.
  - First and last coordinate pair MUST be identical (closed ring).
  - GeoJSON standard format: `[longitude, latitude]`.
  - Polygons must not self-intersect and must not overlap with another active zone.
- **Success Response (201 Created)**:
```json
{
  "success": true,
  "message": "Delivery zone created successfully",
  "data": {
    "publicId": "7ba85f64-5717-4562-b3fc-2c963f66bb89",
    "zoneName": "Kurnool North Zone",
    "city": "Kurnool",
    "state": "Andhra Pradesh",
    "deliveryCharge": 35.00,
    "minimumOrderAmount": 200.00,
    "freeDeliveryThreshold": 500.00,
    "estimatedDeliveryTime": "Next morning 7:00 AM - 10:00 AM",
    "active": true,
    "createdAt": "2026-11-10T09:00:00Z"
  },
  "timestamp": "2026-11-10T09:00:00Z"
}
```
- **Error Response (409 Conflict — Overlapping Area)**:
```json
{
  "success": false,
  "message": "Delivery zone polygon overlaps with existing active zone: Kurnool Central Zone",
  "timestamp": "2026-11-10T09:00:00Z"
}
```

---

### 2.5 Admin Update Delivery Zone
- **Method**: `PUT`
- **Gateway URL**: `/api/admin/delivery/zones/{publicId}`
- **Auth**: `ROLE_ADMIN`
- **Request Body**:
```json
{
  "zoneName": "Kurnool North Extended",
  "city": "Kurnool",
  "state": "Andhra Pradesh",
  "coordinates": [
    [78.0300, 15.8200],
    [78.0450, 15.8200],
    [78.0450, 15.8350],
    [78.0300, 15.8350],
    [78.0300, 15.8200]
  ],
  "deliveryCharge": 40.00,
  "minimumOrderAmount": 250.00,
  "freeDeliveryThreshold": 600.00,
  "estimatedDeliveryTime": "Next morning 7:00 AM - 10:00 AM",
  "description": "Expanded north coverage"
}
```

---

### 2.6 Admin Enable / Disable Zone
- **Method**: `PATCH`
- **Gateway URL**: `/api/admin/delivery/zones/{publicId}/status`
- **Auth**: `ROLE_ADMIN`
- **Request Body**:
```json
{
  "active": false
}
```
- **Success Response (200 OK)**:
```json
{
  "success": true,
  "message": "Zone status updated successfully",
  "data": {
    "publicId": "7ba85f64-5717-4562-b3fc-2c963f66bb89",
    "active": false
  },
  "timestamp": "2026-11-10T09:05:00Z"
}
```

---

## 3. Order Service APIs

### 3.1 Get Order Acceptance Operating Window (Public / Customer)
- **Method**: `GET`
- **Gateway URL**: `/api/orders/operating-window`
- **Auth**: None (Public)
- **Success Response (200 OK)**:
```json
{
  "success": true,
  "message": "Order business settings fetched successfully",
  "data": {
    "orderAcceptanceEnabled": true,
    "openingTime": "08:00:00",
    "closingTime": "21:00:00",
    "timezone": "Asia/Kolkata",
    "deliveryType": "NEXT_DAY",
    "deliveryDaysOffset": 1,
    "updatedAt": "2026-11-10T08:00:00Z"
  },
  "timestamp": "2026-11-10T09:00:00Z"
}
```

---

### 3.2 Customer Create Order
- **Method**: `POST`
- **Gateway URL**: `/api/orders`
- **Auth**: `ROLE_CUSTOMER`
- **Headers**:
  - `Authorization: Bearer <customer_jwt>`
  - `Content-Type: application/json`
- **Request Body**:
```json
{
  "deliveryAddress": {
    "recipientName": "Ramesh Kumar",
    "phoneNumber": "9876543210",
    "addressLine1": "Flat 402, Green Meadows",
    "addressLine2": "Near Collectorate",
    "areaLocality": "Gandhi Nagar",
    "city": "Kurnool",
    "state": "Andhra Pradesh",
    "pincode": "518002",
    "latitude": 15.8281,
    "longitude": 78.0373
  },
  "paymentMethod": "COD",
  "idempotencyKey": "9f32148b-3e5e-4c7b-91d1-6677889900aa",
  "deliveryNotes": "Please ring bell and leave with security"
}
```
- **Validation Rules**:
  - `paymentMethod`: `COD` or `ONLINE_FULL`
  - `idempotencyKey`: Unique client UUID to prevent duplicate charges on double-clicks.
  - `latitude`/`longitude`: Required for zone resolution.
- **Success Response (201 Created)**:
```json
{
  "success": true,
  "message": "Order placed successfully",
  "data": {
    "orderPublicId": "550e8400-e29b-41d4-a716-446655440000",
    "orderNumber": "ORD-20261110-A1B2C",
    "customerPublicId": "cust-12345",
    "orderStatus": "CONFIRMED",
    "paymentStatus": "PENDING",
    "paymentMethod": "COD",
    "subtotalAmount": 340.00,
    "deliveryFee": 30.00,
    "discountAmount": 0.00,
    "totalAmount": 370.00,
    "promisedDeliveryDate": "2026-11-11",
    "deliveryType": "NEXT_DAY",
    "deliveryZoneName": "Kurnool Central Zone",
    "deliveryAddress": {
      "recipientName": "Ramesh Kumar",
      "phoneNumber": "9876543210",
      "addressLine1": "Flat 402, Green Meadows",
      "city": "Kurnool",
      "state": "Andhra Pradesh",
      "pincode": "518002",
      "latitude": 15.8281,
      "longitude": 78.0373
    },
    "items": [
      {
        "productPublicId": "prod-apple-1",
        "productName": "Shimla Fresh Apples",
        "unit": "KG",
        "quantity": 2.0,
        "unitPrice": 120.00,
        "totalPrice": 240.00,
        "imageUrl": "https://cdn.rrvgreenbasket.com/apples.jpg"
      },
      {
        "productPublicId": "prod-tomato-1",
        "productName": "Country Tomatoes",
        "unit": "KG",
        "quantity": 2.5,
        "unitPrice": 40.00,
        "totalPrice": 100.00,
        "imageUrl": "https://cdn.rrvgreenbasket.com/tomatoes.jpg"
      }
    ],
    "orderPlacedAt": "2026-11-10T14:30:00Z"
  },
  "timestamp": "2026-11-10T14:30:00Z"
}
```
- **Error Response (400 Bad Request — Operating Window Closed)**:
```json
{
  "success": false,
  "message": "Order placement is currently closed. Orders are accepted between 8:00 AM and 9:00 PM.",
  "data": {
    "orderAcceptanceEnabled": true,
    "openingTime": "08:00",
    "closingTime": "21:00",
    "nextOpeningTime": "08:00"
  },
  "timestamp": "2026-11-10T21:15:00Z"
}
```
- **Error Response (422 Unprocessable Entity — Below Minimum Order Amount)**:
```json
{
  "success": false,
  "message": "Minimum order amount for this delivery zone is ₹199.00. Current cart subtotal: ₹150.00",
  "timestamp": "2026-11-10T14:30:00Z"
}
```
- **Error Response (409 Conflict — Idempotency Key Already Processed)**:
```json
{
  "success": false,
  "message": "An order with idempotency key '9f32148b-3e5e-4c7b-91d1-6677889900aa' has already been processed",
  "timestamp": "2026-11-10T14:30:01Z"
}
```

---

### 3.3 Customer Order History & Tracking
- **Get My Orders**: `GET /api/orders/my-orders?page=0&size=10&sort=orderPlacedAt,desc`
- **Get Order Details**: `GET /api/orders/{orderPublicId}`
- **Get Order Status History**: `GET /api/orders/{orderPublicId}/status`
- **Customer Cancel Order**:
  - `PATCH /api/orders/{orderPublicId}/cancel`
  - Body:
```json
{
  "reason": "Ordered duplicate items by mistake"
}
```
  - Cancellation is permitted while `orderStatus` is `PLACED` or `CONFIRMED`.
  - Automatically releases reserved inventory back into stock.

---

## 4. Admin Order Management APIs

### 4.1 Admin Orders Query
- **Method**: `GET`
- **Gateway URL**: `/api/admin/orders`
- **Auth**: `ROLE_ADMIN`
- **Query Parameters**:
  - `search`: Filter by order number or recipient name
  - `status`: `PLACED`, `CONFIRMED`, `PROCESSING`, `PACKED`, `OUT_FOR_DELIVERY`, `DELIVERED`, `CANCELLED`
  - `customerPublicId`: Filter by customer
  - `fromDate`: `YYYY-MM-DD`
  - `toDate`: `YYYY-MM-DD`
  - `deliveryDate`: `YYYY-MM-DD`
  - `page`: Page index (default 0)
  - `size`: Page size (default 20)
  - `sort`: Sort string (e.g. `orderPlacedAt,desc`)

---

### 4.2 Admin Order Status Transition
- **Method**: `PATCH`
- **Gateway URL**: `/api/admin/orders/{orderPublicId}/status`
- **Auth**: `ROLE_ADMIN`
- **Request Body**:
```json
{
  "status": "PROCESSING",
  "reason": "Batch verified and sent to fulfillment floor"
}
```
- **Lifecycle Transition Rules**:
  - `PLACED` → `CONFIRMED`, `CANCELLED`, `FAILED`
  - `CONFIRMED` → `PROCESSING`, `CANCELLED`
  - `PROCESSING` → `PACKED`, `CANCELLED`
  - `PACKED` → `OUT_FOR_DELIVERY`
  - `OUT_FOR_DELIVERY` → `DELIVERED`, `CANCELLED`
  - `DELIVERED`, `CANCELLED`, `FAILED` are terminal states.

---

### 4.3 Admin Order & Revenue Statistics
- **Method**: `GET`
- **Gateway URL**: `/api/admin/orders/statistics`
- **Auth**: `ROLE_ADMIN`
- **Success Response (200 OK)**:
```json
{
  "success": true,
  "message": "Order statistics fetched successfully",
  "data": {
    "totalOrders": 1250,
    "todayOrders": 85,
    "pendingOrders": 12,
    "confirmedOrders": 45,
    "processingOrders": 28,
    "packedOrders": 0,
    "outForDeliveryOrders": 0,
    "deliveredOrders": 1140,
    "cancelledOrders": 25,
    "totalRevenue": 482500.00,
    "todayRevenue": 34800.00
  },
  "timestamp": "2026-11-10T18:00:00Z"
}
```

---

### 4.4 Admin Business Settings Management
- **Get Settings**: `GET /api/admin/orders/settings`
- **Update Settings**: `PUT /api/admin/orders/settings`
```json
{
  "orderAcceptanceEnabled": true,
  "openingTime": "08:00",
  "closingTime": "21:00",
  "deliveryType": "NEXT_DAY",
  "deliveryDaysOffset": 1,
  "timezone": "Asia/Kolkata"
}
```
- **Toggle Acceptance**: `PATCH /api/admin/orders/settings/status`
```json
{
  "orderAcceptanceEnabled": false
}
```
