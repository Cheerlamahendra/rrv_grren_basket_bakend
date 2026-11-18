package com.rrvgreenbasket.delivery.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.delivery.dto.request.DeliveryPricingRequest;
import com.rrvgreenbasket.delivery.dto.request.UpdateStoreSettingsRequest;
import com.rrvgreenbasket.delivery.dto.response.DeliveryPricingResponse;
import com.rrvgreenbasket.delivery.dto.response.StoreLocationResponse;
import com.rrvgreenbasket.delivery.security.HeaderAuthenticationFilter;
import com.rrvgreenbasket.delivery.service.DeliveryPricingService;
import com.rrvgreenbasket.delivery.service.StoreLocationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminDeliveryController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("AdminDeliveryController Tests")
class AdminDeliveryControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StoreLocationService storeLocationService;

    @MockBean
    private DeliveryPricingService deliveryPricingService;

    @MockBean
    private HeaderAuthenticationFilter headerAuthenticationFilter;

    @Test
    @DisplayName("GET /api/admin/delivery/settings returns store configuration")
    void testGetSettings() throws Exception {
        StoreLocationResponse response = StoreLocationResponse.builder()
                .storeId("store-kurnool-primary")
                .storeName("RRV Green Basket - Kurnool Central")
                .latitude(15.8281)
                .longitude(78.0373)
                .maximumDeliveryDistanceKm(5.0)
                .deliveryEnabled(true)
                .currency("INR")
                .build();

        when(storeLocationService.getStoreLocationResponse()).thenReturn(response);

        mockMvc.perform(get("/api/admin/delivery/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.storeName").value("RRV Green Basket - Kurnool Central"));
    }

    @Test
    @DisplayName("PUT /api/admin/delivery/settings updates store settings")
    void testUpdateSettings() throws Exception {
        UpdateStoreSettingsRequest request = UpdateStoreSettingsRequest.builder()
                .storeName("RRV Green Basket - New Hub")
                .formattedAddress("Station Road, Kurnool, Andhra Pradesh 518001")
                .latitude(15.8300)
                .longitude(78.0400)
                .city("Kurnool")
                .state("Andhra Pradesh")
                .postalCode("518001")
                .country("India")
                .maximumDeliveryDistanceMeters(6000)
                .deliveryEnabled(true)
                .currency("INR")
                .build();

        StoreLocationResponse updated = StoreLocationResponse.builder()
                .storeId("store-kurnool-primary")
                .storeName("RRV Green Basket - New Hub")
                .maximumDeliveryDistanceKm(6.0)
                .build();

        when(storeLocationService.updateStoreSettings(any(UpdateStoreSettingsRequest.class), any())).thenReturn(updated);

        mockMvc.perform(put("/api/admin/delivery/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.storeName").value("RRV Green Basket - New Hub"));
    }

    @Test
    @DisplayName("PATCH /api/admin/delivery/status toggles delivery acceptance")
    void testToggleStatus() throws Exception {
        StoreLocationResponse updated = StoreLocationResponse.builder()
                .storeId("store-kurnool-primary")
                .deliveryEnabled(false)
                .build();

        when(storeLocationService.updateDeliveryStatus(eq(false), any())).thenReturn(updated);

        mockMvc.perform(patch("/api/admin/delivery/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("deliveryEnabled", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Delivery acceptance disabled successfully"));
    }

    @Test
    @DisplayName("GET /api/admin/delivery/pricing returns all slabs")
    void testGetPricingSlabs() throws Exception {
        DeliveryPricingResponse slab = DeliveryPricingResponse.builder()
                .id(1L)
                .minimumDistanceMeters(0)
                .maximumDistanceMeters(1000)
                .deliveryCharge(new BigDecimal("10.00"))
                .currency("INR")
                .active(true)
                .build();

        when(deliveryPricingService.getAllPricingSlabs()).thenReturn(List.of(slab));

        mockMvc.perform(get("/api/admin/delivery/pricing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].deliveryCharge").value(10.00));
    }

    @Test
    @DisplayName("POST /api/admin/delivery/pricing creates new pricing slab")
    void testCreatePricingSlab() throws Exception {
        DeliveryPricingRequest request = DeliveryPricingRequest.builder()
                .minimumDistanceMeters(5001)
                .maximumDistanceMeters(6000)
                .deliveryCharge(new BigDecimal("35.00"))
                .currency("INR")
                .active(true)
                .build();

        DeliveryPricingResponse created = DeliveryPricingResponse.builder()
                .id(6L)
                .minimumDistanceMeters(5001)
                .maximumDistanceMeters(6000)
                .deliveryCharge(new BigDecimal("35.00"))
                .currency("INR")
                .active(true)
                .build();

        when(deliveryPricingService.createPricingSlab(any(DeliveryPricingRequest.class), any())).thenReturn(created);

        mockMvc.perform(post("/api/admin/delivery/pricing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(6))
                .andExpect(jsonPath("$.data.deliveryCharge").value(35.00));
    }

    @Test
    @DisplayName("DELETE /api/admin/delivery/pricing/{id} deactivates slab")
    void testDeletePricingSlab() throws Exception {
        mockMvc.perform(delete("/api/admin/delivery/pricing/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Delivery pricing slab deactivated successfully"));

        verify(deliveryPricingService).deleteOrDeactivatePricingSlab(eq(1L), any());
    }
}
