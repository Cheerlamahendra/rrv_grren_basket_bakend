package com.rrvgreenbasket.order.controller;

import com.rrvgreenbasket.order.dto.request.UpdateOrderStatusRequest;
import com.rrvgreenbasket.order.dto.response.ApiResponse;
import com.rrvgreenbasket.order.dto.response.OrderResponse;
import com.rrvgreenbasket.order.dto.response.OrderStatisticsResponse;
import com.rrvgreenbasket.order.dto.response.PageResponse;
import com.rrvgreenbasket.order.enums.OrderStatus;
import com.rrvgreenbasket.order.security.UserPrincipal;
import com.rrvgreenbasket.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@Tag(name = "Admin Orders", description = "Admin order lifecycle management, filtering, and reporting APIs")
public class AdminOrderController {

    private final OrderService orderService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Search and list all orders with filters and pagination")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getOrders(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String customerPublicId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveryDate,
            @PageableDefault(size = 20, sort = "orderPlacedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        log.info("Admin query orders: search='{}', status={}, customer='{}', from={}, to={}, deliveryDate={}",
                search, status, customerPublicId, fromDate, toDate, deliveryDate);
        PageResponse<OrderResponse> page = orderService.getAdminOrders(
                search, status, customerPublicId, fromDate, toDate, deliveryDate, pageable
        );
        return ResponseEntity.ok(ApiResponse.success("Orders fetched successfully", page));
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Get order and revenue statistics for admin dashboard")
    public ResponseEntity<ApiResponse<OrderStatisticsResponse>> getStatistics() {
        log.info("Admin request for order statistics");
        OrderStatisticsResponse stats = orderService.getAdminStatistics();
        return ResponseEntity.ok(ApiResponse.success("Order statistics fetched successfully", stats));
    }

    @GetMapping("/{orderPublicId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Get complete order details including delivery zone and items")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderDetails(
            @PathVariable String orderPublicId
    ) {
        log.info("Admin query order details for: {}", orderPublicId);
        OrderResponse response = orderService.getAdminOrderDetails(orderPublicId);
        return ResponseEntity.ok(ApiResponse.success("Order details fetched successfully", response));
    }

    @PatchMapping("/{orderPublicId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Update order status with transition validation")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @PathVariable String orderPublicId,
            @Valid @RequestBody UpdateOrderStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        String adminUser = (principal != null) ? principal.getUsername() : "ADMIN";
        log.info("Admin '{}' updating status of order '{}' to: {}", adminUser, orderPublicId, request.getStatus());
        OrderResponse response = orderService.updateOrderStatus(
                orderPublicId, request.getStatus(), request.getEffectiveReason(), adminUser
        );
        return ResponseEntity.ok(ApiResponse.success("Order status updated successfully", response));
    }

    @DeleteMapping("/{orderPublicId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Permanently delete a cancelled order")
    public ResponseEntity<ApiResponse<Void>> deleteCancelledOrder(
            @PathVariable String orderPublicId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        String adminUser = (principal != null) ? principal.getUsername() : "ADMIN";
        log.info("Admin '{}' permanently deleting cancelled order '{}'", adminUser, orderPublicId);
        orderService.deleteCancelledOrder(orderPublicId, adminUser);
        return ResponseEntity.ok(ApiResponse.success("Cancelled order permanently deleted successfully", null));
    }
}
