package com.rrvgreenbasket.delivery.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.delivery.dto.request.CheckDeliveryAvailabilityRequest;
import com.rrvgreenbasket.delivery.dto.response.DeliveryAvailabilityResponse;
import com.rrvgreenbasket.delivery.service.DeliveryEligibilityService;
import com.rrvgreenbasket.delivery.service.DeliveryZoneService;
import com.rrvgreenbasket.delivery.service.StoreLocationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("test")
@DisplayName("Security Access Control Tests")
class SecurityAccessIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DeliveryZoneService deliveryZoneService;

    @MockBean
    private DeliveryEligibilityService deliveryEligibilityService;

    @MockBean
    private StoreLocationService storeLocationService;

    @Test
    @DisplayName("Public endpoint /api/delivery/check-availability allows unauthenticated access")
    void testPublicEndpoint_Unauthenticated_Allowed() throws Exception {
        CheckDeliveryAvailabilityRequest request = CheckDeliveryAvailabilityRequest.builder()
                .latitude(15.8281)
                .longitude(78.0373)
                .build();

        DeliveryAvailabilityResponse response = DeliveryAvailabilityResponse.builder()
                .available(true)
                .message("Delivery available")
                .build();

        when(deliveryEligibilityService.checkAvailability(any(CheckDeliveryAvailabilityRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/delivery/check-availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Admin endpoint /api/admin/delivery/settings rejects unauthenticated access with 401")
    void testAdminEndpoint_Unauthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/delivery/settings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "customer-1", roles = {"CUSTOMER"})
    @DisplayName("Admin endpoint /api/admin/delivery/settings rejects customer role with 403")
    void testAdminEndpoint_CustomerRole_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/delivery/settings"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin-1", roles = {"ADMIN"})
    @DisplayName("Admin endpoint /api/admin/delivery/settings allows admin role")
    void testAdminEndpoint_AdminRole_Allowed() throws Exception {
        mockMvc.perform(get("/api/admin/delivery/settings"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Legacy admin endpoint /api/admin/delivery/zones rejects unauthenticated access with 401")
    void testLegacyAdminEndpoint_Unauthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/delivery/zones"))
                .andExpect(status().isUnauthorized());
    }
}
