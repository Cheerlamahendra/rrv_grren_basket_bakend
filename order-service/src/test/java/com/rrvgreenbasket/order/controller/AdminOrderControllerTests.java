package com.rrvgreenbasket.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.order.dto.request.UpdateOrderStatusRequest;
import com.rrvgreenbasket.order.dto.response.OrderResponse;
import com.rrvgreenbasket.order.dto.response.OrderStatisticsResponse;
import com.rrvgreenbasket.order.dto.response.PageResponse;
import com.rrvgreenbasket.order.enums.OrderStatus;
import com.rrvgreenbasket.order.security.HeaderAuthenticationFilter;
import com.rrvgreenbasket.order.security.UserPrincipal;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminOrderController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AdminOrderController Tests")
class AdminOrderControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @MockBean
    private HeaderAuthenticationFilter headerAuthenticationFilter;

    private UsernamePasswordAuthenticationToken getAdminAuth() {
        UserPrincipal principal = new UserPrincipal("admin-1", "ROLE_ADMIN", "session-1");
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }

    @Test
    @DisplayName("GET /api/admin/orders lists orders with filters")
    void testGetOrders() throws Exception {
        OrderResponse order = OrderResponse.builder()
                .orderPublicId("ord-1")
                .orderNumber("ORD-10001")
                .orderStatus(OrderStatus.PLACED)
                .totalAmount(new BigDecimal("350.00"))
                .build();

        PageResponse<OrderResponse> page = PageResponse.from(new PageImpl<>(List.of(order)));

        when(orderService.getAdminOrders(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/admin/orders")
                        .with(authentication(getAdminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].orderPublicId").value("ord-1"));
    }

    @Test
    @DisplayName("GET /api/admin/orders/statistics returns order stats")
    void testGetStatistics() throws Exception {
        OrderStatisticsResponse stats = OrderStatisticsResponse.builder()
                .totalOrders(100L)
                .todayOrders(15L)
                .pendingOrders(5L)
                .confirmedOrders(10L)
                .deliveredOrders(80L)
                .totalRevenue(new BigDecimal("35000.00"))
                .build();

        when(orderService.getAdminStatistics()).thenReturn(stats);

        mockMvc.perform(get("/api/admin/orders/statistics")
                        .with(authentication(getAdminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalOrders").value(100))
                .andExpect(jsonPath("$.data.totalRevenue").value(35000.00));
    }

    @Test
    @DisplayName("PATCH /api/admin/orders/{orderPublicId}/status transitions order status")
    void testUpdateStatus() throws Exception {
        UpdateOrderStatusRequest request = UpdateOrderStatusRequest.builder()
                .status(OrderStatus.CONFIRMED)
                .reason("Verified payment")
                .build();

        OrderResponse response = OrderResponse.builder()
                .orderPublicId("ord-1")
                .orderStatus(OrderStatus.CONFIRMED)
                .build();

        when(orderService.updateOrderStatus(eq("ord-1"), eq(OrderStatus.CONFIRMED), eq("Verified payment"), any()))
                .thenReturn(response);

        mockMvc.perform(patch("/api/admin/orders/ord-1/status")
                        .with(authentication(getAdminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderStatus").value("CONFIRMED"));
    }

    @Test
    @DisplayName("PATCH /api/admin/orders/{orderNumber}/status transitions order status using orderNumber and remarks")
    void testUpdateStatusWithOrderNumberAndRemarks() throws Exception {
        String jsonPayload = "{\"status\":\"Processing\",\"remarks\":\"Order is being prepared\"}";

        OrderResponse response = OrderResponse.builder()
                .orderPublicId("ord-uuid-1")
                .orderNumber("RRV-20261121-63588")
                .orderStatus(OrderStatus.PROCESSING)
                .build();

        when(orderService.updateOrderStatus(eq("RRV-20261121-63588"), eq(OrderStatus.PROCESSING), eq("Order is being prepared"), any()))
                .thenReturn(response);

        mockMvc.perform(patch("/api/admin/orders/RRV-20261121-63588/status")
                        .with(authentication(getAdminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderNumber").value("RRV-20261121-63588"))
                .andExpect(jsonPath("$.data.orderStatus").value("PROCESSING"));
    }

    @Test
    @DisplayName("DELETE /api/admin/orders/{orderPublicId} permanently deletes cancelled order")
    void testDeleteCancelledOrder() throws Exception {
        mockMvc.perform(delete("/api/admin/orders/ord-1")
                        .with(authentication(getAdminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Cancelled order permanently deleted successfully"));
    }
}
