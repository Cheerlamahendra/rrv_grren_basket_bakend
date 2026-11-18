package com.rrvgreenbasket.order.controller;

import com.rrvgreenbasket.order.dto.request.PaymentEventRequest;
import com.rrvgreenbasket.order.dto.response.ApiResponse;
import com.rrvgreenbasket.order.dto.response.OrderDetailsDto;
import com.rrvgreenbasket.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/internal/orders")
@RequiredArgsConstructor
@Tag(name = "Internal Orders", description = "Service-to-service internal APIs for Payment and Notification Services")
public class InternalOrderController {

    private final OrderService orderService;

    @GetMapping("/{orderPublicId}")
    @PreAuthorize("hasAnyRole('INTERNAL_SERVICE', 'ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Get order details for payment processing")
    public ResponseEntity<ApiResponse<OrderDetailsDto>> getOrderForPayment(
            @PathVariable String orderPublicId
    ) {
        log.info("Internal request: fetch order details for payment of: {}", orderPublicId);
        OrderDetailsDto details = orderService.getOrderForPayment(orderPublicId);
        return ResponseEntity.ok(ApiResponse.success("Order details fetched for payment", details));
    }

    @PostMapping("/{orderPublicId}/payment-events")
    @PreAuthorize("hasAnyRole('INTERNAL_SERVICE', 'ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Receive payment webhook / event notification from Payment Service")
    public ResponseEntity<ApiResponse<Void>> handlePaymentEvent(
            @PathVariable String orderPublicId,
            @RequestBody PaymentEventRequest request
    ) {
        log.info("Internal request: handle payment event '{}' for order: {}", request.getEventType(), orderPublicId);
        orderService.handlePaymentEvent(orderPublicId, request);
        return ResponseEntity.ok(ApiResponse.success("Payment event processed successfully"));
    }
}
