package com.rrvgreenbasket.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.order.client.CartServiceClient;
import com.rrvgreenbasket.order.client.CustomerServiceClient;
import com.rrvgreenbasket.order.client.DeliveryServiceClient;
import com.rrvgreenbasket.order.client.ProductServiceClient;
import com.rrvgreenbasket.order.client.dto.*;
import com.rrvgreenbasket.order.dto.request.AddressDto;
import com.rrvgreenbasket.order.dto.request.CreateOrderRequest;
import com.rrvgreenbasket.order.dto.request.PaymentEventRequest;
import com.rrvgreenbasket.order.dto.response.OrderDetailsDto;
import com.rrvgreenbasket.order.dto.response.OrderResponse;
import com.rrvgreenbasket.order.dto.response.OrderStatisticsResponse;
import com.rrvgreenbasket.order.entity.Order;
import com.rrvgreenbasket.order.entity.OrderBusinessSettings;
import com.rrvgreenbasket.order.entity.OrderStatusHistory;
import com.rrvgreenbasket.order.enums.DeliveryType;
import com.rrvgreenbasket.order.enums.OrderStatus;
import com.rrvgreenbasket.order.enums.PaymentMethod;
import com.rrvgreenbasket.order.enums.PaymentStatus;
import com.rrvgreenbasket.order.exception.InvalidOrderStatusTransitionException;
import com.rrvgreenbasket.order.exception.OrderNotFoundException;
import com.rrvgreenbasket.order.exception.OrderValidationException;
import com.rrvgreenbasket.order.exception.UnauthorizedOrderAccessException;
import com.rrvgreenbasket.order.repository.OrderItemRepository;
import com.rrvgreenbasket.order.repository.OrderRepository;
import com.rrvgreenbasket.order.repository.OrderStatusHistoryRepository;
import com.rrvgreenbasket.order.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceTests {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderStatusHistoryRepository statusHistoryRepository;

    @Mock
    private OrderBusinessSettingsService businessSettingsService;

    @Mock
    private CartServiceClient cartServiceClient;

    @Mock
    private ProductServiceClient productServiceClient;

    @Mock
    private DeliveryServiceClient deliveryServiceClient;

    @Mock
    private CustomerServiceClient customerServiceClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Clock clock;
    private OrderServiceImpl orderService;

    private CreateOrderRequest createRequest;
    private CartClientResponse cartResponse;
    private DeliveryAvailabilityClientResponse zoneResponse;
    private OrderBusinessSettings defaultSettings;

    @BeforeEach
    void setUp() {
        AddressDto address = AddressDto.builder()
                .addressLine("123 Green Street")
                .landmark("Gandhi Nagar")
                .city("Kurnool")
                .state("Andhra Pradesh")
                .pincode("518001")
                .latitude(new BigDecimal("15.8281"))
                .longitude(new BigDecimal("78.0373"))
                .build();

        createRequest = CreateOrderRequest.builder()
                .deliveryAddress(address)
                .paymentMethod(PaymentMethod.COD)
                .idempotencyKey("idem-test-12345")
                .notes("Leave at front gate")
                .build();

        CartItemClientDto cartItem = CartItemClientDto.builder()
                .productPublicId("prod-apple-1")
                .productName("Shimla Apple")
                .unitOfMeasure("KG")
                .quantity(new BigDecimal("2.0"))
                .pricePerKg(new BigDecimal("120.00"))
                .itemSubtotal(new BigDecimal("240.00"))
                .build();

        cartResponse = CartClientResponse.builder()
                .customerPublicId("cust-1")
                .items(java.util.List.of(cartItem))
                .subtotal(new BigDecimal("240.00"))
                .build();

        zoneResponse = DeliveryAvailabilityClientResponse.builder()
                .available(true)
                .zonePublicId("zone-knl-1")
                .zoneName("Kurnool Central")
                .deliveryCharge(new BigDecimal("30.00"))
                .minimumOrderAmount(new BigDecimal("100.00"))
                .freeDeliveryThreshold(new BigDecimal("500.00"))
                .estimatedDeliveryTime("Next morning by 8:00 AM")
                .build();

        defaultSettings = OrderBusinessSettings.builder()
                .id(1L)
                .publicId("settings-1")
                .orderAcceptanceEnabled(true)
                .openingTime(LocalTime.of(6, 0))
                .closingTime(LocalTime.of(22, 0))
                .timezone("Asia/Kolkata")
                .deliveryType(DeliveryType.NEXT_DAY)
                .deliveryDaysOffset(1)
                .build();

        when(businessSettingsService.getSettingsEntity()).thenReturn(defaultSettings);
        when(businessSettingsService.getCurrentBusinessDateTime())
                .thenReturn(ZonedDateTime.of(2026, 11, 10, 10, 0, 0, 0, ZoneId.of("Asia/Kolkata")));
        when(businessSettingsService.calculatePromisedDeliveryDate(any(LocalDate.class)))
                .thenReturn(LocalDate.of(2026, 11, 11));

        clock = Clock.fixed(
                ZonedDateTime.of(2026, 11, 10, 10, 0, 0, 0, ZoneId.of("Asia/Kolkata")).toInstant(),
                ZoneId.of("Asia/Kolkata")
        );

        orderService = new OrderServiceImpl(
                orderRepository,
                orderItemRepository,
                statusHistoryRepository,
                businessSettingsService,
                cartServiceClient,
                productServiceClient,
                deliveryServiceClient,
                customerServiceClient,
                objectMapper,
                clock
        );
    }

    private void setClockTime(LocalDateTime localDateTime) {
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        Clock fixed = Clock.fixed(localDateTime.atZone(zone).toInstant(), zone);
        orderService = new OrderServiceImpl(
                orderRepository,
                orderItemRepository,
                statusHistoryRepository,
                businessSettingsService,
                cartServiceClient,
                productServiceClient,
                deliveryServiceClient,
                customerServiceClient,
                objectMapper,
                fixed
        );
    }

    @Test
    @DisplayName("Should create COD order successfully with CONFIRMED status and no Inventory Service call")
    void shouldCreateCodOrderSuccessfullyWithoutInventory() {
        doNothing().when(businessSettingsService).validateOrderAcceptanceWindow();
        when(deliveryServiceClient.checkAvailability(anyDouble(), anyDouble(), any())).thenReturn(zoneResponse);
        when(cartServiceClient.getCustomerCart("cust-1")).thenReturn(cartResponse);

        ProductDetailClientResponse prodDetail = ProductDetailClientResponse.builder()
                .publicId("prod-apple-1")
                .name("Shimla Apple")
                .pricePerKg(new BigDecimal("120.00"))
                .unitOfMeasure("KG")
                .productImageUrl("https://cdn.example.com/apple.jpg")
                .isAvailableForSale(true)
                .build();
        when(productServiceClient.getProductDetails("prod-apple-1")).thenReturn(prodDetail);

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(100L);
            return o;
        });

        OrderResponse response = orderService.createOrder(createRequest, "cust-1");

        assertThat(response).isNotNull();
        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(response.getPaymentMethod()).isEqualTo(PaymentMethod.COD);
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.COD_PENDING);
        assertThat(response.getTotalAmount()).isEqualByComparingTo("270.00");
        assertThat(response.getPromisedDeliveryDate()).isEqualTo(LocalDate.of(2026, 11, 11));

        verify(cartServiceClient).clearCart("cust-1");
        verify(statusHistoryRepository).save(any(OrderStatusHistory.class));
    }

    @Test
    @DisplayName("Should create online payment order with PLACED status and PENDING payment")
    void shouldCreateOnlineOrderSuccessfullyWithoutInventory() {
        createRequest.setPaymentMethod(PaymentMethod.ONLINE_FULL);

        doNothing().when(businessSettingsService).validateOrderAcceptanceWindow();
        when(deliveryServiceClient.checkAvailability(anyDouble(), anyDouble(), any())).thenReturn(zoneResponse);
        when(cartServiceClient.getCustomerCart("cust-1")).thenReturn(cartResponse);

        ProductDetailClientResponse prodDetail = ProductDetailClientResponse.builder()
                .publicId("prod-apple-1")
                .name("Shimla Apple")
                .pricePerKg(new BigDecimal("120.00"))
                .unitOfMeasure("KG")
                .productImageUrl("https://cdn.example.com/apple.jpg")
                .isAvailableForSale(true)
                .build();
        when(productServiceClient.getProductDetails("prod-apple-1")).thenReturn(prodDetail);

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(101L);
            return o;
        });

        OrderResponse response = orderService.createOrder(createRequest, "cust-1");

        assertThat(response).isNotNull();
        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(response.getPaymentMethod()).isEqualTo(PaymentMethod.ONLINE_FULL);
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);

        verify(cartServiceClient).clearCart("cust-1");
    }

    @Test
    @DisplayName("Should reject order creation when cart is empty")
    void shouldRejectWhenCartIsEmpty() {
        cartResponse.setItems(new ArrayList<>());
        doNothing().when(businessSettingsService).validateOrderAcceptanceWindow();
        when(cartServiceClient.getCustomerCart("cust-1")).thenReturn(cartResponse);

        assertThatThrownBy(() -> orderService.createOrder(createRequest, "cust-1"))
                .isInstanceOf(OrderValidationException.class)
                .hasMessageContaining("Cannot place order with an empty shopping cart");
    }

    @Test
    @DisplayName("Should reject order creation when delivery location is out of coverage")
    void shouldRejectWhenLocationOutOfCoverage() {
        zoneResponse.setAvailable(false);
        zoneResponse.setMessage("No delivery zone covers these coordinates");

        doNothing().when(businessSettingsService).validateOrderAcceptanceWindow();
        when(cartServiceClient.getCustomerCart("cust-1")).thenReturn(cartResponse);
        when(deliveryServiceClient.checkAvailability(anyDouble(), anyDouble(), any())).thenReturn(zoneResponse);

        assertThatThrownBy(() -> orderService.createOrder(createRequest, "cust-1"))
                .isInstanceOf(OrderValidationException.class)
                .hasMessageContaining("Delivery is not available at the selected location");
    }

    @Test
    @DisplayName("Should return existing order on duplicate idempotency key (Idempotency)")
    void shouldHandleDuplicateIdempotencyKey() {
        Order existing = Order.builder()
                .id(999L)
                .publicId("ord-existing")
                .orderNumber("ORD-999")
                .customerPublicId("cust-1")
                .orderStatus(OrderStatus.CONFIRMED)
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.PENDING)
                .items(new ArrayList<>())
                .subtotal(new BigDecimal("240.00"))
                .deliveryCharge(new BigDecimal("30.00"))
                .totalAmount(new BigDecimal("270.00"))
                .orderPlacedAt(LocalDateTime.of(2026, 11, 10, 10, 0))
                .build();

        when(orderRepository.findByCustomerPublicIdAndIdempotencyKey("cust-1", "idem-test-12345"))
                .thenReturn(Optional.of(existing));

        OrderResponse response = orderService.createOrder(createRequest, "cust-1");

        assertThat(response).isNotNull();
        assertThat(response.getOrderPublicId()).isEqualTo("ord-existing");
        assertThat(response.getOrderNumber()).isEqualTo("ORD-999");
    }

    @Test
    @DisplayName("Should allow customer same-day cancellation within store operating hours")
    void shouldAllowSameDayCancellationWithinOperatingHours() {
        setClockTime(LocalDateTime.of(2026, 11, 10, 14, 0, 0)); // 2:00 PM same day

        Order existingOrder = Order.builder()
                .id(1L)
                .publicId("ord-test-1")
                .orderNumber("ORD-10001")
                .customerPublicId("cust-1")
                .orderStatus(OrderStatus.PLACED)
                .paymentStatus(PaymentStatus.PENDING)
                .totalAmount(new BigDecimal("250.00"))
                .orderPlacedAt(LocalDateTime.of(2026, 11, 10, 8, 30, 0))
                .items(new ArrayList<>())
                .build();

        when(orderRepository.findByPublicId("ord-test-1")).thenReturn(Optional.of(existingOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        OrderResponse cancelled = orderService.cancelOrder("ord-test-1", "cust-1", "Changed my mind");

        assertThat(cancelled.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.getCancellationReason()).isEqualTo("Changed my mind");
        assertThat(cancelled.getCancelledAt()).isNotNull();
        verify(statusHistoryRepository).save(any(OrderStatusHistory.class));
    }

    @Test
    @DisplayName("Should reject customer cancellation on the next calendar day")
    void shouldRejectCancellationOnNextDay() {
        setClockTime(LocalDateTime.of(2026, 11, 11, 8, 0, 0)); // Next day at 8:00 AM

        Order order = Order.builder()
                .id(1L)
                .publicId("ord-test-1")
                .customerPublicId("cust-1")
                .orderStatus(OrderStatus.PLACED)
                .orderPlacedAt(LocalDateTime.of(2026, 11, 10, 21, 50, 0)) // Placed previous day
                .items(new ArrayList<>())
                .build();

        when(orderRepository.findByPublicId("ord-test-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder("ord-test-1", "cust-1", "Cancel next day"))
                .isInstanceOf(OrderValidationException.class)
                .hasMessageContaining("Order cancellation is allowed only on the order placement date.");
    }

    @Test
    @DisplayName("Should reject customer cancellation before store opening time (e.g. 5:59 AM)")
    void shouldRejectCancellationBeforeOpeningTime() {
        setClockTime(LocalDateTime.of(2026, 11, 10, 5, 59, 0)); // 5:59 AM (store opens at 6:00 AM)

        Order order = Order.builder()
                .id(1L)
                .publicId("ord-test-1")
                .customerPublicId("cust-1")
                .orderStatus(OrderStatus.CONFIRMED)
                .orderPlacedAt(LocalDateTime.of(2026, 11, 10, 5, 50, 0))
                .items(new ArrayList<>())
                .build();

        when(orderRepository.findByPublicId("ord-test-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder("ord-test-1", "cust-1", "Early morning"))
                .isInstanceOf(OrderValidationException.class)
                .hasMessageContaining("Order cancellation is available only during store operating hours.");
    }

    @Test
    @DisplayName("Should reject customer cancellation at exact closing time (e.g. 10:00 PM)")
    void shouldRejectCancellationAtExactClosingTime() {
        setClockTime(LocalDateTime.of(2026, 11, 10, 22, 0, 0)); // 10:00 PM (store closes at 22:00)

        Order order = Order.builder()
                .id(1L)
                .publicId("ord-test-1")
                .customerPublicId("cust-1")
                .orderStatus(OrderStatus.CONFIRMED)
                .orderPlacedAt(LocalDateTime.of(2026, 11, 10, 21, 30, 0))
                .items(new ArrayList<>())
                .build();

        when(orderRepository.findByPublicId("ord-test-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder("ord-test-1", "cust-1", "At closing"))
                .isInstanceOf(OrderValidationException.class)
                .hasMessageContaining("Order cancellation is available only during store operating hours.");
    }

    @Test
    @DisplayName("Should allow customer cancellation immediately before closing time (e.g. 9:59:59 PM)")
    void shouldAllowCancellationImmediatelyBeforeClosingTime() {
        setClockTime(LocalDateTime.of(2026, 11, 10, 21, 59, 59));

        Order order = Order.builder()
                .id(1L)
                .publicId("ord-test-1")
                .customerPublicId("cust-1")
                .orderStatus(OrderStatus.CONFIRMED)
                .orderPlacedAt(LocalDateTime.of(2026, 11, 10, 21, 50, 0))
                .items(new ArrayList<>())
                .build();

        when(orderRepository.findByPublicId("ord-test-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        OrderResponse response = orderService.cancelOrder("ord-test-1", "cust-1", "Last minute cancel");
        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("Should allow customer cancellation exactly at opening time (e.g. 6:00 AM)")
    void shouldAllowCancellationAtExactOpeningTime() {
        setClockTime(LocalDateTime.of(2026, 11, 10, 6, 0, 0));

        Order order = Order.builder()
                .id(1L)
                .publicId("ord-test-1")
                .customerPublicId("cust-1")
                .orderStatus(OrderStatus.PLACED)
                .orderPlacedAt(LocalDateTime.of(2026, 11, 10, 6, 0, 0))
                .items(new ArrayList<>())
                .build();

        when(orderRepository.findByPublicId("ord-test-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        OrderResponse response = orderService.cancelOrder("ord-test-1", "cust-1", "At opening");
        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("Should reject customer cancellation when order is in DELIVERED status")
    void shouldRejectCancellationForDeliveredOrder() {
        setClockTime(LocalDateTime.of(2026, 11, 10, 10, 0, 0));

        Order existingOrder = Order.builder()
                .id(1L)
                .publicId("ord-test-1")
                .customerPublicId("cust-1")
                .orderStatus(OrderStatus.DELIVERED)
                .orderPlacedAt(LocalDateTime.of(2026, 11, 10, 8, 0, 0))
                .items(new ArrayList<>())
                .build();

        when(orderRepository.findByPublicId("ord-test-1")).thenReturn(Optional.of(existingOrder));

        assertThatThrownBy(() -> orderService.cancelOrder("ord-test-1", "cust-1", "Cannot cancel"))
                .isInstanceOf(InvalidOrderStatusTransitionException.class)
                .hasMessageContaining("Order cannot be cancelled in its current state: DELIVERED");
    }

    @Test
    @DisplayName("Should reject customer accessing or cancelling another customer's order")
    void shouldRejectUnauthorizedCustomerAccess() {
        Order existingOrder = Order.builder()
                .id(1L)
                .publicId("ord-test-1")
                .customerPublicId("cust-owner-99")
                .orderStatus(OrderStatus.PLACED)
                .items(new ArrayList<>())
                .build();

        when(orderRepository.findByPublicId("ord-test-1")).thenReturn(Optional.of(existingOrder));

        assertThatThrownBy(() -> orderService.cancelOrder("ord-test-1", "cust-hacker", "Not mine"))
                .isInstanceOf(UnauthorizedOrderAccessException.class);
    }

    @Test
    @DisplayName("Admin can update order status following state machine rules without Inventory Service")
    void shouldUpdateOrderStatusByAdmin() {
        Order existingOrder = Order.builder()
                .id(1L)
                .publicId("ord-test-1")
                .orderStatus(OrderStatus.PLACED)
                .items(new ArrayList<>())
                .orderPlacedAt(LocalDateTime.of(2026, 11, 10, 9, 0))
                .build();

        when(orderRepository.findByPublicId("ord-test-1")).thenReturn(Optional.of(existingOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        OrderResponse response = orderService.updateOrderStatus(
                "ord-test-1", OrderStatus.CONFIRMED, "Admin approved", "admin-alice"
        );

        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("Admin updating order status to CANCELLED records cancellation timestamp without Inventory Service")
    void shouldAllowAdminToCancelOrder() {
        Order existingOrder = Order.builder()
                .id(1L)
                .publicId("ord-test-1")
                .orderStatus(OrderStatus.CONFIRMED)
                .items(new ArrayList<>())
                .orderPlacedAt(LocalDateTime.of(2026, 11, 10, 9, 0))
                .build();

        when(orderRepository.findByPublicId("ord-test-1")).thenReturn(Optional.of(existingOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        OrderResponse response = orderService.updateOrderStatus(
                "ord-test-1", OrderStatus.CANCELLED, "Customer called to cancel", "admin-alice"
        );

        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(existingOrder.getCancelledAt()).isNotNull();
        assertThat(existingOrder.getCancellationReason()).isEqualTo("Customer called to cancel");
    }

    @Test
    @DisplayName("Admin can permanently delete a CANCELLED order transactionally")
    void shouldPermanentlyDeleteCancelledOrder() {
        Order cancelledOrder = Order.builder()
                .id(55L)
                .publicId("ord-cancelled-1")
                .orderStatus(OrderStatus.CANCELLED)
                .items(new ArrayList<>())
                .build();

        when(orderRepository.findByPublicId("ord-cancelled-1")).thenReturn(Optional.of(cancelledOrder));
        doNothing().when(statusHistoryRepository).deleteByOrderId(55L);
        doNothing().when(orderRepository).delete(cancelledOrder);

        orderService.deleteCancelledOrder("ord-cancelled-1", "admin-bob");

        verify(statusHistoryRepository).deleteByOrderId(55L);
        verify(orderRepository).delete(cancelledOrder);
    }

    @Test
    @DisplayName("Admin cannot permanently delete a non-cancelled order")
    void shouldRejectDeletionOfNonCancelledOrder() {
        Order activeOrder = Order.builder()
                .id(66L)
                .publicId("ord-active-1")
                .orderStatus(OrderStatus.CONFIRMED)
                .items(new ArrayList<>())
                .build();

        when(orderRepository.findByPublicId("ord-active-1")).thenReturn(Optional.of(activeOrder));

        assertThatThrownBy(() -> orderService.deleteCancelledOrder("ord-active-1", "admin-bob"))
                .isInstanceOf(OrderValidationException.class)
                .hasMessageContaining("Only cancelled orders can be permanently deleted");

        verify(orderRepository, never()).delete(any(Order.class));
        verify(statusHistoryRepository, never()).deleteByOrderId(any());
    }

    @Test
    @DisplayName("OrderResponse includes cancellation eligibility fields based on business hours")
    void shouldPopulateCancellationEligibilityInOrderResponse() {
        setClockTime(LocalDateTime.of(2026, 11, 10, 15, 0, 0));

        Order order = Order.builder()
                .id(1L)
                .publicId("ord-test-1")
                .customerPublicId("cust-1")
                .orderStatus(OrderStatus.PLACED)
                .orderPlacedAt(LocalDateTime.of(2026, 11, 10, 10, 0, 0))
                .items(new ArrayList<>())
                .build();

        when(orderRepository.findByPublicId("ord-test-1")).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrderDetails("ord-test-1", "cust-1");

        assertThat(response.getCancellationAllowed()).isTrue();
        assertThat(response.getCancellationDeadline()).isEqualTo(LocalDateTime.of(2026, 11, 10, 22, 0, 0));
        assertThat(response.getOrderDate()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(response.getCurrentStoreOpeningTime()).isEqualTo(LocalTime.of(6, 0));
        assertThat(response.getCurrentStoreClosingTime()).isEqualTo(LocalTime.of(22, 0));
    }

    @Test
    @DisplayName("Internal payment event PAYMENT_SUCCESS should mark payment COMPLETED and confirm order without Inventory")
    void shouldHandlePaymentSuccessEvent() {
        Order existingOrder = Order.builder()
                .id(1L)
                .publicId("ord-test-1")
                .orderStatus(OrderStatus.PLACED)
                .paymentStatus(PaymentStatus.PENDING)
                .items(new ArrayList<>())
                .build();

        when(orderRepository.findByPublicId("ord-test-1")).thenReturn(Optional.of(existingOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        PaymentEventRequest event = PaymentEventRequest.builder()
                .eventType("PAYMENT_SUCCESS")
                .status("COMPLETED")
                .paymentId("txn-rzp-12345")
                .amount(new BigDecimal("250.00"))
                .build();

        orderService.handlePaymentEvent("ord-test-1", event);

        assertThat(existingOrder.getPaymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(existingOrder.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("Internal payment event PAYMENT_FAILED should mark payment FAILED and order FAILED without Inventory")
    void shouldHandlePaymentFailedEvent() {
        Order existingOrder = Order.builder()
                .id(1L)
                .publicId("ord-test-1")
                .orderStatus(OrderStatus.PLACED)
                .paymentStatus(PaymentStatus.PENDING)
                .items(new ArrayList<>())
                .build();

        when(orderRepository.findByPublicId("ord-test-1")).thenReturn(Optional.of(existingOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        PaymentEventRequest event = PaymentEventRequest.builder()
                .eventType("PAYMENT_FAILED")
                .status("FAILED")
                .failureReason("Insufficient funds")
                .amount(new BigDecimal("250.00"))
                .build();

        orderService.handlePaymentEvent("ord-test-1", event);

        assertThat(existingOrder.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(existingOrder.getOrderStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(existingOrder.getCancellationReason()).contains("Payment failure: Insufficient funds");
    }

    @Test
    @DisplayName("Should return order statistics for admin dashboard")
    void shouldReturnAdminStatistics() {
        when(orderRepository.count()).thenReturn(150L);
        when(orderRepository.countByOrderPlacedAtBetween(any(), any())).thenReturn(25L);
        when(orderRepository.countByOrderStatus(OrderStatus.PLACED)).thenReturn(10L);
        when(orderRepository.countByOrderStatus(OrderStatus.CONFIRMED)).thenReturn(15L);
        when(orderRepository.countByOrderStatus(OrderStatus.PROCESSING)).thenReturn(5L);
        when(orderRepository.countByOrderStatus(OrderStatus.PACKED)).thenReturn(8L);
        when(orderRepository.countByOrderStatus(OrderStatus.OUT_FOR_DELIVERY)).thenReturn(4L);
        when(orderRepository.countByOrderStatus(OrderStatus.DELIVERED)).thenReturn(100L);
        when(orderRepository.countByOrderStatus(OrderStatus.CANCELLED)).thenReturn(8L);
        when(orderRepository.sumTotalRevenue()).thenReturn(new BigDecimal("45200.00"));
        when(orderRepository.sumRevenueBetween(any(), any())).thenReturn(new BigDecimal("7500.00"));

        OrderStatisticsResponse stats = orderService.getAdminStatistics();

        assertThat(stats.getTotalOrders()).isEqualTo(150L);
        assertThat(stats.getTodayOrders()).isEqualTo(25L);
        assertThat(stats.getTotalRevenue()).isEqualByComparingTo("45200.00");
        assertThat(stats.getTodayRevenue()).isEqualByComparingTo("7500.00");
    }

    @Test
    @DisplayName("Should return OrderDetailsDto for payment service")
    void shouldReturnOrderForPayment() {
        Order existingOrder = Order.builder()
                .id(1L)
                .publicId("ord-test-1")
                .customerPublicId("cust-1")
                .orderStatus(OrderStatus.PLACED)
                .paymentStatus(PaymentStatus.PENDING)
                .paymentMethod(PaymentMethod.ONLINE_FULL)
                .totalAmount(new BigDecimal("350.00"))
                .items(new ArrayList<>())
                .build();

        when(orderRepository.findByPublicId("ord-test-1")).thenReturn(Optional.of(existingOrder));

        OrderDetailsDto dto = orderService.getOrderForPayment("ord-test-1");

        assertThat(dto.getOrderPublicId()).isEqualTo("ord-test-1");
        assertThat(dto.isPaymentEligible()).isTrue();
        assertThat(dto.getTotalAmount()).isEqualByComparingTo("350.00");
    }

    @Test
    @DisplayName("Should persist customer profile and complete delivery address snapshot with receiver customization")
    void shouldPersistCompleteAddressSnapshotAndCustomerProfileOnOrderCreation() {
        doNothing().when(businessSettingsService).validateOrderAcceptanceWindow();
        when(deliveryServiceClient.checkAvailability(anyDouble(), anyDouble(), any())).thenReturn(zoneResponse);
        when(orderRepository.findByCustomerPublicIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(cartServiceClient.getCustomerCart("cust-1")).thenReturn(cartResponse);

        ProductDetailClientResponse prodDetail = ProductDetailClientResponse.builder()
                .publicId("prod-apple-1")
                .name("Shimla Apple")
                .pricePerKg(new BigDecimal("120.00"))
                .unitOfMeasure("KG")
                .productImageUrl("https://cdn.example.com/apple.jpg")
                .isAvailableForSale(true)
                .build();
        when(productServiceClient.getProductDetails("prod-apple-1")).thenReturn(prodDetail);

        // Mock authoritative customer profile
        CustomerClientResponse mockProfile = CustomerClientResponse.builder()
                .id("cust-1")
                .fullName("Rajesh Kumar")
                .mobileNumber("9876543210")
                .email("rajesh@example.com")
                .build();
        when(customerServiceClient.getCustomerProfile("cust-1")).thenReturn(mockProfile);

        // Mock saved customer address
        AddressClientResponse savedAddress = AddressClientResponse.builder()
                .id("addr-saved-101")
                .receiverName("Rajesh Home")
                .receiverMobile("9876543210")
                .addressLine1("Flat 101, Green Meadows")
                .addressLine2("Near Water Tank")
                .landmark("Opp Metro")
                .city("Kurnool")
                .state("Andhra Pradesh")
                .pincode("518001")
                .country("India")
                .build();
        when(customerServiceClient.getAddress("addr-saved-101", "cust-1")).thenReturn(savedAddress);

        // Customer customizes recipient for this order
        AddressDto customOrderAddress = AddressDto.builder()
                .addressPublicId("addr-saved-101")
                .recipientName("Anand (Hostel Mate)")
                .recipientMobile("8978040537")
                .addressLine1("Flat 101, Green Meadows")
                .addressLine2("Near Water Tank")
                .landmark("Opp Metro")
                .city("Kurnool")
                .state("Andhra Pradesh")
                .pincode("518001")
                .country("India")
                .latitude(new BigDecimal("15.8281"))
                .longitude(new BigDecimal("78.0373"))
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .deliveryAddress(customOrderAddress)
                .paymentMethod(PaymentMethod.COD)
                .idempotencyKey("idem-custom-addr-1")
                .notes("Deliver to Anand")
                .build();

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(101L);
            order.setPublicId("ord-snapshot-test-1");
            return order;
        });

        OrderResponse response = orderService.createOrder(request, "cust-1");

        // 1. Verify registered customer details are snapshotted
        assertThat(response.getRegisteredCustomerName()).isEqualTo("Rajesh Kumar");
        assertThat(response.getRegisteredCustomerMobile()).isEqualTo("9876543210");
        assertThat(response.getCustomer()).isNotNull();
        assertThat(response.getCustomer().getRegisteredName()).isEqualTo("Rajesh Kumar");
        assertThat(response.getCustomer().getRegisteredMobile()).isEqualTo("9876543210");

        // 2. Verify recipient customizations are present in order response
        assertThat(response.getRecipientName()).isEqualTo("Anand (Hostel Mate)");
        assertThat(response.getRecipientMobile()).isEqualTo("8978040537");
        assertThat(response.getDeliveryAddress()).isNotNull();
        assertThat(response.getDeliveryAddress().getRecipientName()).isEqualTo("Anand (Hostel Mate)");
        assertThat(response.getDeliveryAddress().getRecipientMobile()).isEqualTo("8978040537");
        assertThat(response.getDeliveryAddress().getAddressLine1()).isEqualTo("Flat 101, Green Meadows");
        assertThat(response.getDeliveryAddress().getCity()).isEqualTo("Kurnool");
        assertThat(response.getDeliveryAddress().getPincode()).isEqualTo("518001");
        assertThat(response.getDeliveryAddress().getAddressPublicId()).isEqualTo("addr-saved-101");

        // 3. Verify order items have both id and orderItemPublicId for stable React keys
        assertThat(response.getItems()).isNotEmpty();
        assertThat(response.getItems().get(0).getId()).isNotNull();
        assertThat(response.getItems().get(0).getOrderItemPublicId()).isNotNull();

        // 4. Verify original saved address was NOT modified via customerServiceClient (no saveAddress or update call made)
        verify(customerServiceClient, never()).saveAddress(any(), any());
    }

    @Test
    @DisplayName("Should update order status using orderNumber identifier and record remarks in history")
    void shouldUpdateOrderStatusUsingOrderNumberIdentifier() {
        Order existingOrder = Order.builder()
                .id(202L)
                .publicId("ord-uuid-202")
                .orderNumber("RRV-20261121-63588")
                .customerPublicId("cust-1")
                .orderStatus(OrderStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.PENDING)
                .paymentMethod(PaymentMethod.COD)
                .totalAmount(new BigDecimal("450.00"))
                .items(new ArrayList<>())
                .build();

        when(orderRepository.findByOrderNumber("RRV-20261121-63588")).thenReturn(Optional.of(existingOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse updated = orderService.updateOrderStatus(
                "RRV-20261121-63588",
                OrderStatus.PROCESSING,
                "Items picked and packed",
                "admin-ops"
        );

        assertThat(updated.getOrderNumber()).isEqualTo("RRV-20261121-63588");
        assertThat(updated.getOrderStatus()).isEqualTo(OrderStatus.PROCESSING);

        // Verify status history was saved
        verify(statusHistoryRepository).save(argThat(history ->
                history.getOrderId().equals(202L)
                        && history.getNewStatus() == OrderStatus.PROCESSING
                        && "Items picked and packed".equals(history.getReason())
        ));
    }
}
