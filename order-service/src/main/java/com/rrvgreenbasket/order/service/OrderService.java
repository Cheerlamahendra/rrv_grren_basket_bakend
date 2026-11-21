package com.rrvgreenbasket.order.service;

import com.rrvgreenbasket.order.dto.request.CreateOrderRequest;
import com.rrvgreenbasket.order.dto.request.PaymentEventRequest;
import com.rrvgreenbasket.order.dto.response.*;
import com.rrvgreenbasket.order.enums.OrderStatus;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface OrderService {

    OrderResponse createOrder(CreateOrderRequest request, String customerPublicId);

    PageResponse<OrderResponse> getCustomerOrders(String customerPublicId, Pageable pageable);

    OrderResponse getOrderDetails(String orderPublicId, String customerPublicId);

    OrderResponse cancelOrder(String orderPublicId, String customerPublicId, String reason);

    OrderStatusHistoryResponse getLatestOrderStatus(String orderPublicId, String customerPublicId);

    PageResponse<OrderResponse> getAdminOrders(
            String search,
            OrderStatus status,
            String customerPublicId,
            LocalDate fromDate,
            LocalDate toDate,
            LocalDate deliveryDate,
            Pageable pageable
    );

    OrderResponse getAdminOrderDetails(String orderPublicId);

    OrderResponse updateOrderStatus(String orderPublicId, OrderStatus newStatus, String reason, String changedBy);

    void deleteCancelledOrder(String orderPublicId, String adminUser);

    OrderStatisticsResponse getAdminStatistics();

    OrderDetailsDto getOrderForPayment(String orderPublicId);

    void handlePaymentEvent(String orderPublicId, PaymentEventRequest request);
}
