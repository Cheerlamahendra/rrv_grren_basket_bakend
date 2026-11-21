package com.rrvgreenbasket.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.order.dto.request.AddressDto;
import com.rrvgreenbasket.order.dto.request.CancelOrderRequest;
import com.rrvgreenbasket.order.dto.request.CreateOrderRequest;
import com.rrvgreenbasket.order.dto.response.OrderBusinessSettingsResponse;
import com.rrvgreenbasket.order.dto.response.OrderResponse;
import com.rrvgreenbasket.order.dto.response.OrderStatusHistoryResponse;
import com.rrvgreenbasket.order.dto.response.PageResponse;
import com.rrvgreenbasket.order.enums.DeliveryType;
import com.rrvgreenbasket.order.enums.OrderStatus;
import com.rrvgreenbasket.order.enums.PaymentMethod;
import com.rrvgreenbasket.order.enums.PaymentStatus;
import com.rrvgreenbasket.order.security.HeaderAuthenticationFilter;
import com.rrvgreenbasket.order.security.UserPrincipal;
import com.rrvgreenbasket.order.service.OrderBusinessSettingsService;
import com.rrvgreenbasket.order.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CustomerOrderController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("CustomerOrderController Tests")
class CustomerOrderControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @MockBean
    private OrderBusinessSettingsService businessSettingsService;

    @MockBean
    private HeaderAuthenticationFilter headerAuthenticationFilter;

    private UserPrincipal createMockCustomerPrincipal() {
        return new UserPrincipal("cust-12345", "ROLE_CUSTOMER", "session-1");
    }

    private UsernamePasswordAuthenticationToken getAuthToken() {
        UserPrincipal principal = createMockCustomerPrincipal();
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
        );
    }

    @Test
    @DisplayName("GET /api/orders/operating-window returns current window settings")
    void testGetOperatingWindow() throws Exception {
        OrderBusinessSettingsResponse settings = OrderBusinessSettingsResponse.builder()
                .publicId("b4c5d6e7-f8a9-4b0c-1d2e-3f4a5b6c7d8e")
                .orderAcceptanceEnabled(true)
                .openingTime(LocalTime.of(6, 0))
                .closingTime(LocalTime.of(22, 0))
                .timezone("Asia/Kolkata")
                .deliveryType(DeliveryType.NEXT_DAY)
                .deliveryDaysOffset(1)
                .build();

        when(businessSettingsService.getSettings()).thenReturn(settings);

        mockMvc.perform(get("/api/orders/operating-window"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderAcceptanceEnabled").value(true))
                .andExpect(jsonPath("$.data.openingTime").value("06:00"))
                .andExpect(jsonPath("$.data.closingTime").value("22:00"))
                .andExpect(jsonPath("$.data.timezone").value("Asia/Kolkata"));
    }

    @Test
    @DisplayName("POST /api/orders creates a new customer order")
    void testCreateOrder() throws Exception {
        AddressDto address = AddressDto.builder()
                .addressLine("123 Green Street")
                .landmark("Gandhi Nagar")
                .city("Kurnool")
                .state("Andhra Pradesh")
                .pincode("518001")
                .latitude(new BigDecimal("15.8281"))
                .longitude(new BigDecimal("78.0373"))
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .deliveryAddress(address)
                .paymentMethod(PaymentMethod.COD)
                .idempotencyKey("idem-unit-1")
                .build();

        OrderResponse response = OrderResponse.builder()
                .orderPublicId("ord-test-abc")
                .orderNumber("ORD-10001")
                .orderStatus(OrderStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.PENDING)
                .paymentMethod(PaymentMethod.COD)
                .totalAmount(new BigDecimal("280.00"))
                .promisedDeliveryDate(LocalDate.now().plusDays(1))
                .build();

        when(orderService.createOrder(any(CreateOrderRequest.class), eq("cust-12345"))).thenReturn(response);

        mockMvc.perform(post("/api/orders")
                        .with(authentication(getAuthToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderPublicId").value("ord-test-abc"))
                .andExpect(jsonPath("$.data.orderNumber").value("ORD-10001"));
    }

    @Test
    @DisplayName("GET /api/orders/my-orders returns customer order list")
    void testGetMyOrders() throws Exception {
        OrderResponse response = OrderResponse.builder()
                .orderPublicId("ord-test-abc")
                .orderNumber("ORD-10001")
                .orderStatus(OrderStatus.PLACED)
                .totalAmount(new BigDecimal("280.00"))
                .build();

        PageResponse<OrderResponse> page = PageResponse.from(new PageImpl<>(List.of(response)));

        when(orderService.getCustomerOrders(eq("cust-12345"), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/orders/my-orders")
                        .with(authentication(getAuthToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].orderPublicId").value("ord-test-abc"));
    }

    @Test
    @DisplayName("PATCH /api/orders/{orderPublicId}/cancel cancels customer order")
    void testCancelOrder() throws Exception {
        CancelOrderRequest request = CancelOrderRequest.builder()
                .reason("Placed by mistake")
                .build();

        OrderResponse response = OrderResponse.builder()
                .orderPublicId("ord-test-abc")
                .orderStatus(OrderStatus.CANCELLED)
                .cancellationReason("Placed by mistake")
                .build();

        when(orderService.cancelOrder(eq("ord-test-abc"), eq("cust-12345"), eq("Placed by mistake"))).thenReturn(response);

        mockMvc.perform(patch("/api/orders/ord-test-abc/cancel")
                        .with(authentication(getAuthToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderStatus").value("CANCELLED"));
    }

    @Test
    @DisplayName("GET /api/orders/{orderPublicId}/status returns order milestone history")
    void testGetOrderStatus() throws Exception {
        OrderStatusHistoryResponse response = OrderStatusHistoryResponse.builder()
                .previousStatus(null)
                .newStatus(OrderStatus.PLACED)
                .changedBy("SYSTEM")
                .reason("Order placed successfully")
                .timestamp(LocalDateTime.now())
                .build();

        when(orderService.getLatestOrderStatus(eq("ord-test-abc"), eq("cust-12345"))).thenReturn(response);

        mockMvc.perform(get("/api/orders/ord-test-abc/status")
                        .with(authentication(getAuthToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.newStatus").value("PLACED"));
    }
}
