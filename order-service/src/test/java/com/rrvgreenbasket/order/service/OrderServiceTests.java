package com.rrvgreenbasket.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.order.client.CartServiceClient;
import com.rrvgreenbasket.order.client.DeliveryServiceClient;
import com.rrvgreenbasket.order.client.InventoryServiceClient;
import com.rrvgreenbasket.order.client.ProductServiceClient;
import com.rrvgreenbasket.order.client.dto.*;
import com.rrvgreenbasket.order.dto.request.AddressDto;
import com.rrvgreenbasket.order.dto.request.CreateOrderRequest;
import com.rrvgreenbasket.order.dto.request.PaymentEventRequest;
import com.rrvgreenbasket.order.dto.response.OrderDetailsDto;
import com.rrvgreenbasket.order.dto.response.OrderResponse;
import com.rrvgreenbasket.order.dto.response.OrderStatisticsResponse;
import com.rrvgreenbasket.order.entity.Order;
import com.rrvgreenbasket.order.entity.OrderStatusHistory;
import com.rrvgreenbasket.order.enums.OrderStatus;
import com.rrvgreenbasket.order.enums.PaymentMethod;
import com.rrvgreenbasket.order.enums.PaymentStatus;
import com.rrvgreenbasket.order.exception.IdempotencyConflictException;
import com.rrvgreenbasket.order.exception.InvalidOrderStatusTransitionException;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
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
    private InventoryServiceClient inventoryServiceClient;

    @Mock
    private DeliveryServiceClient deliveryServiceClient;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private OrderServiceImpl orderService;

    private CreateOrderRequest createRequest;
    private CartClientResponse cartResponse;
    private DeliveryAvailabilityClientResponse zoneResponse;

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

        when(inventoryServiceClient.confirmReservation(anyString())).thenReturn(
                ReservationClientResponse.builder().status("CONFIRMED").build()
        );
        when(inventoryServiceClient.releaseReservation(anyString())).thenReturn(
                ReservationClientResponse.builder().status("RELEASED").build()
        );
    }

    @Test
    @DisplayName("Should create COD order successfully with auto-confirmation and next-day delivery")
    void shouldCreateCodOrderSuccessfully() {
        doNothing().when(businessSettingsService).validateOrderAcceptanceWindow();
        when(businessSettingsService.getCurrentBusinessDateTime())
                .thenReturn(ZonedDateTime.of(2026, 11, 10, 10, 0, 0, 0, ZoneId.of("Asia/Kolkata")));
        when(businessSettingsService.calculatePromisedDeliveryDate(any(LocalDate.class)))
                .thenReturn(LocalDate.of(2026, 11, 11));

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

        when(inventoryServiceClient.createReservation(any())).thenReturn(
                ReservationClientResponse.builder()
                        .publicId("res-1")
                        .status("RESERVED")
                        .build()
        );

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(100L);
            return o;
        });

        OrderResponse response = orderService.createOrder(createRequest, "cust-1");

        assertThat(response).isNotNull();
        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(response.getPaymentMethod()).isEqualTo(PaymentMethod.COD);
        assertThat(response.getSubtotal()).isEqualByComparingTo("240.00");
        assertThat(response.getDeliveryCharge()).isEqualByComparingTo("30.00");
        assertThat(response.getTotalAmount()).isEqualByComparingTo("270.00");
        assertThat(response.getDeliveryZoneName()).isEqualTo("Kurnool Central");

        verify(cartServiceClient).clearCart("cust-1");
        verify(inventoryServiceClient).confirmReservation(anyString());
    }

    @Test
    @DisplayName("Should waive delivery fee when subtotal meets free delivery threshold")
    void shouldApplyFreeDeliveryWhenThresholdMet() {
        zoneResponse.setFreeDeliveryThreshold(new BigDecimal("200.00"));

        doNothing().when(businessSettingsService).validateOrderAcceptanceWindow();
        when(businessSettingsService.getCurrentBusinessDateTime())
                .thenReturn(ZonedDateTime.of(2026, 11, 10, 10, 0, 0, 0, ZoneId.of("Asia/Kolkata")));
        when(businessSettingsService.calculatePromisedDeliveryDate(any(LocalDate.class)))
                .thenReturn(LocalDate.of(2026, 11, 11));

        when(deliveryServiceClient.checkAvailability(anyDouble(), anyDouble(), any())).thenReturn(zoneResponse);
        when(cartServiceClient.getCustomerCart("cust-1")).thenReturn(cartResponse);

        ProductDetailClientResponse prodDetail = ProductDetailClientResponse.builder()
                .publicId("prod-apple-1")
                .name("Shimla Apple")
                .pricePerKg(new BigDecimal("120.00"))
                .unitOfMeasure("KG")
                .isAvailableForSale(true)
                .build();
        when(productServiceClient.getProductDetails("prod-apple-1")).thenReturn(prodDetail);

        when(inventoryServiceClient.createReservation(any())).thenReturn(
                ReservationClientResponse.builder().publicId("res-1").status("RESERVED").build()
        );
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> {
            Order o = i.getArgument(0);
            o.setId(101L);
            return o;
        });

        OrderResponse response = orderService.createOrder(createRequest, "cust-1");

        assertThat(response.getDeliveryCharge()).isEqualByComparingTo("0.00");
        assertThat(response.getTotalAmount()).isEqualByComparingTo("240.00");
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
                .build();

        when(orderRepository.findByCustomerPublicIdAndIdempotencyKey("cust-1", "idem-test-12345"))
                .thenReturn(Optional.of(existing));

        OrderResponse response = orderService.createOrder(createRequest, "cust-1");

        assertThat(response).isNotNull();
        assertThat(response.getOrderPublicId()).isEqualTo("ord-existing");
        assertThat(response.getOrderNumber()).isEqualTo("ORD-999");
    }

    @Test
    @DisplayName("Should allow customer to cancel their own order in PLACED status")
    void shouldAllowCustomerCancellation() {
        Order existingOrder = Order.builder()
                .id(1L)
                .publicId("ord-test-1")
                .orderNumber("ORD-10001")
                .customerPublicId("cust-1")
                .orderStatus(OrderStatus.PLACED)
                .paymentStatus(PaymentStatus.PENDING)
                .totalAmount(new BigDecimal("250.00"))
                .items(new ArrayList<>())
                .build();

        when(orderRepository.findByPublicId("ord-test-1")).thenReturn(Optional.of(existingOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        OrderResponse cancelled = orderService.cancelOrder("ord-test-1", "cust-1", "Changed my mind");

        assertThat(cancelled.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventoryServiceClient).releaseReservation("ord-test-1");
        verify(statusHistoryRepository).save(any(OrderStatusHistory.class));
    }

    @Test
    @DisplayName("Should reject customer cancellation when order is already DELIVERED")
    void shouldRejectCancellationForDeliveredOrder() {
        Order existingOrder = Order.builder()
                .id(1L)
                .publicId("ord-test-1")
                .customerPublicId("cust-1")
                .orderStatus(OrderStatus.DELIVERED)
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
    @DisplayName("Admin can update order status following state machine rules")
    void shouldUpdateOrderStatusByAdmin() {
        Order existingOrder = Order.builder()
                .id(1L)
                .publicId("ord-test-1")
                .orderStatus(OrderStatus.PLACED)
                .items(new ArrayList<>())
                .build();

        when(orderRepository.findByPublicId("ord-test-1")).thenReturn(Optional.of(existingOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        OrderResponse response = orderService.updateOrderStatus(
                "ord-test-1", OrderStatus.CONFIRMED, "Admin approved", "admin-alice"
        );

        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(inventoryServiceClient).confirmReservation("ord-test-1");
    }

    @Test
    @DisplayName("Admin updating order status to CANCELLED triggers inventory release")
    void shouldReleaseInventoryWhenAdminCancels() {
        Order existingOrder = Order.builder()
                .id(1L)
                .publicId("ord-test-1")
                .orderStatus(OrderStatus.CONFIRMED)
                .items(new ArrayList<>())
                .build();

        when(orderRepository.findByPublicId("ord-test-1")).thenReturn(Optional.of(existingOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        OrderResponse response = orderService.updateOrderStatus(
                "ord-test-1", OrderStatus.CANCELLED, "Customer called to cancel", "admin-alice"
        );

        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventoryServiceClient).releaseReservation("ord-test-1");
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
    @DisplayName("Internal payment event PAYMENT_SUCCESS should mark payment COMPLETED and confirm inventory")
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
        verify(inventoryServiceClient).confirmReservation("ord-test-1");
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
}
