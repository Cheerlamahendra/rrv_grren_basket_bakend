package com.rrvgreenbasket.order.controller;

import com.rrvgreenbasket.order.dto.request.CancelOrderRequest;
import com.rrvgreenbasket.order.dto.request.CreateOrderRequest;
import com.rrvgreenbasket.order.dto.response.*;
import com.rrvgreenbasket.order.security.UserPrincipal;
import com.rrvgreenbasket.order.service.OrderBusinessSettingsService;
import com.rrvgreenbasket.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Customer Orders", description = "Customer Order placement, tracking, and cancellation APIs")
public class CustomerOrderController {

    private final OrderService orderService;
    private final OrderBusinessSettingsService businessSettingsService;

    @GetMapping("/operating-window")
    @Operation(summary = "Get current order acceptance operating window", description = "Public/Customer endpoint to check order hours and cutoff")
    public ResponseEntity<ApiResponse<OrderBusinessSettingsResponse>> getOperatingWindow() {
        OrderBusinessSettingsResponse settings = businessSettingsService.getSettings();
        return ResponseEntity.ok(ApiResponse.success("Order business settings fetched successfully", settings));
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Create an order", description = "Validates operating window, delivery coordinates, cart, and product availability")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        String customerId = (principal != null) ? principal.getUserId() : "cust-12345";
        log.info("REST request to create order for customer: {}", customerId);
        OrderResponse response = orderService.createOrder(request, customerId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Order placed successfully", response));
    }

    @GetMapping("/my-orders")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get my order history", description = "Paginated list of customer orders")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getMyOrders(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20, sort = "orderPlacedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        String customerId = (principal != null) ? principal.getUserId() : "cust-12345";
        log.info("REST request to get orders for customer: {}", customerId);
        PageResponse<OrderResponse> page = orderService.getCustomerOrders(customerId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Orders fetched successfully", page));
    }

    @GetMapping("/{orderPublicId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get order details", description = "Customer can view only their own order")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderDetails(
            @PathVariable String orderPublicId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        String customerId = (principal != null) ? principal.getUserId() : "cust-12345";
        log.info("REST request to get order '{}' for customer: {}", orderPublicId, customerId);
        OrderResponse response = orderService.getOrderDetails(orderPublicId, customerId);
        return ResponseEntity.ok(ApiResponse.success("Order details fetched successfully", response));
    }

    @PatchMapping("/{orderPublicId}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Cancel order", description = "Customer cancels their order before processing cutoff")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @PathVariable String orderPublicId,
            @Valid @RequestBody CancelOrderRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        String customerId = (principal != null) ? principal.getUserId() : "cust-12345";
        log.info("REST request to cancel order '{}' by customer: {}", orderPublicId, customerId);
        OrderResponse response = orderService.cancelOrder(orderPublicId, customerId, request.getReason());
        return ResponseEntity.ok(ApiResponse.success("Order cancelled successfully", response));
    }

    @GetMapping("/{orderPublicId}/status")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get order status and history", description = "Returns current status and complete milestone history")
    public ResponseEntity<ApiResponse<OrderStatusHistoryResponse>> getOrderStatus(
            @PathVariable String orderPublicId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        String customerId = (principal != null) ? principal.getUserId() : "cust-12345";
        log.info("REST request to get status for order '{}' by customer: {}", orderPublicId, customerId);
        OrderStatusHistoryResponse response = orderService.getLatestOrderStatus(orderPublicId, customerId);
        return ResponseEntity.ok(ApiResponse.success("Order status history fetched", response));
    }
}
