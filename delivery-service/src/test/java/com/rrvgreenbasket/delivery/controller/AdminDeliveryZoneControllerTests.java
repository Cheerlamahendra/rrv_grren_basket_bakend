package com.rrvgreenbasket.delivery.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.delivery.dto.request.CreateDeliveryZoneRequest;
import com.rrvgreenbasket.delivery.dto.request.UpdateDeliveryZoneRequest;
import com.rrvgreenbasket.delivery.dto.request.UpdateZoneStatusRequest;
import com.rrvgreenbasket.delivery.dto.response.DeliveryZoneResponse;
import com.rrvgreenbasket.delivery.dto.response.PageResponse;
import com.rrvgreenbasket.delivery.security.HeaderAuthenticationFilter;
import com.rrvgreenbasket.delivery.security.UserPrincipal;
import com.rrvgreenbasket.delivery.service.DeliveryZoneService;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminDeliveryZoneController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("AdminDeliveryZoneController Tests")
class AdminDeliveryZoneControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DeliveryZoneService deliveryZoneService;

    @MockBean
    private HeaderAuthenticationFilter headerAuthenticationFilter;

    @Test
    @DisplayName("POST /api/admin/delivery/zones creates zone")
    void testCreateZone() throws Exception {
        JsonNode boundary = objectMapper.readTree("""
                {
                  "type": "Polygon",
                  "coordinates": [[[78.03, 15.82], [78.05, 15.82], [78.05, 15.84], [78.03, 15.84], [78.03, 15.82]]]
                }
                """);

        CreateDeliveryZoneRequest request = CreateDeliveryZoneRequest.builder()
                .zoneName("Kurnool Zone 1")
                .zoneCode("KUR-ZONE-01")
                .city("Kurnool")
                .state("Andhra Pradesh")
                .deliveryCharge(new BigDecimal("30.00"))
                .minimumOrderAmount(new BigDecimal("199.00"))
                .freeDeliveryThreshold(new BigDecimal("499.00"))
                .estimatedDeliveryTime("NEXT_DAY")
                .active(true)
                .boundary(boundary)
                .build();

        DeliveryZoneResponse response = DeliveryZoneResponse.builder()
                .publicId("zone-1")
                .zoneName("Kurnool Zone 1")
                .zoneCode("KUR-ZONE-01")
                .deliveryCharge(new BigDecimal("30.00"))
                .minimumOrderAmount(new BigDecimal("199.00"))
                .active(true)
                .build();

        UserPrincipal admin = new UserPrincipal("admin-123", "ADMIN", "sess-1");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities()));

        when(deliveryZoneService.createZone(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/admin/delivery/zones")
                        .principal(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.zoneCode").value("KUR-ZONE-01"));
    }

    @Test
    @DisplayName("GET /api/admin/delivery/zones returns paginated list")
    void testGetAllZones() throws Exception {
        DeliveryZoneResponse zone = DeliveryZoneResponse.builder()
                .publicId("zone-1")
                .zoneName("Kurnool Zone 1")
                .build();

        PageResponse<DeliveryZoneResponse> pageResponse = PageResponse.from(new PageImpl<>(List.of(zone)));
        when(deliveryZoneService.getAllZones(any(), any(), any(), any(Pageable.class))).thenReturn(pageResponse);

        mockMvc.perform(get("/api/admin/delivery/zones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].zoneName").value("Kurnool Zone 1"));
    }

    @Test
    @DisplayName("GET /api/admin/delivery/zones/{zonePublicId} returns single zone")
    void testGetZoneById() throws Exception {
        DeliveryZoneResponse response = DeliveryZoneResponse.builder()
                .publicId("zone-1")
                .zoneName("Kurnool Zone 1")
                .city("Kurnool")
                .build();

        when(deliveryZoneService.getZoneById("zone-1")).thenReturn(response);

        mockMvc.perform(get("/api/admin/delivery/zones/zone-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.zoneName").value("Kurnool Zone 1"));
    }

    @Test
    @DisplayName("PUT /api/admin/delivery/zones/{zonePublicId} updates zone")
    void testUpdateZone() throws Exception {
        UpdateDeliveryZoneRequest updateRequest = UpdateDeliveryZoneRequest.builder()
                .zoneName("Updated Kurnool")
                .city("Kurnool")
                .state("Andhra Pradesh")
                .deliveryCharge(new BigDecimal("40.00"))
                .minimumOrderAmount(new BigDecimal("299.00"))
                .estimatedDeliveryTime("SAME_DAY")
                .build();

        DeliveryZoneResponse response = DeliveryZoneResponse.builder()
                .publicId("zone-1")
                .zoneName("Updated Kurnool")
                .deliveryCharge(new BigDecimal("40.00"))
                .build();

        UserPrincipal admin = new UserPrincipal("admin-123", "ADMIN", "sess-1");
        when(deliveryZoneService.updateZone(eq("zone-1"), any(), any())).thenReturn(response);

        mockMvc.perform(put("/api/admin/delivery/zones/zone-1")
                        .principal(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.zoneName").value("Updated Kurnool"));
    }

    @Test
    @DisplayName("PATCH /api/admin/delivery/zones/{zonePublicId}/status activates/deactivates zone")
    void testUpdateZoneStatus() throws Exception {
        UpdateZoneStatusRequest statusRequest = new UpdateZoneStatusRequest(true);
        DeliveryZoneResponse response = DeliveryZoneResponse.builder()
                .publicId("zone-1")
                .active(true)
                .build();

        UserPrincipal admin = new UserPrincipal("admin-123", "ADMIN", "sess-1");
        when(deliveryZoneService.updateZoneStatus(eq("zone-1"), eq(true), any())).thenReturn(response);

        mockMvc.perform(patch("/api/admin/delivery/zones/zone-1/status")
                        .principal(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    @DisplayName("DELETE /api/admin/delivery/zones/{zonePublicId} deactivates zone")
    void testDeleteZone() throws Exception {
        mockMvc.perform(delete("/api/admin/delivery/zones/zone-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(deliveryZoneService).deleteZone("zone-1");
    }
}
