package com.rrvgreenbasket.delivery.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.delivery.dto.request.*;
import com.rrvgreenbasket.delivery.dto.response.*;
import com.rrvgreenbasket.delivery.security.HeaderAuthenticationFilter;
import com.rrvgreenbasket.delivery.service.*;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CustomerDeliveryController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("CustomerDeliveryController Tests")
class CustomerDeliveryControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StoreLocationService storeLocationService;

    @MockBean
    private AddressVerificationService addressVerificationService;

    @MockBean
    private DeliveryEligibilityService deliveryEligibilityService;

    @MockBean
    private DeliveryCheckoutValidationService checkoutValidationService;

    @MockBean
    private CustomerAddressService customerAddressService;

    @MockBean
    private DeliveryZoneService legacyDeliveryZoneService;

    @MockBean
    private HeaderAuthenticationFilter headerAuthenticationFilter;

    @Test
    @DisplayName("GET /api/delivery/store-location returns primary store location")
    void testGetStoreLocation() throws Exception {
        StoreLocationResponse store = StoreLocationResponse.builder()
                .storeId("store-kurnool-primary")
                .storeName("RRV Green Basket - Kurnool Central")
                .latitude(15.8281)
                .longitude(78.0373)
                .maximumDeliveryDistanceKm(5.0)
                .maximumDeliveryDistanceMeters(5000)
                .deliveryEnabled(true)
                .currency("INR")
                .build();

        when(storeLocationService.getStoreLocationResponse()).thenReturn(store);

        mockMvc.perform(get("/api/delivery/store-location"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.storeName").value("RRV Green Basket - Kurnool Central"))
                .andExpect(jsonPath("$.data.maximumDeliveryDistanceKm").value(5.0));
    }

    @Test
    @DisplayName("POST /api/delivery/verify-address verifies Google Place ID")
    void testVerifyAddress() throws Exception {
        VerifyAddressRequest request = VerifyAddressRequest.builder()
                .placeId("ChIJ7eXy0rP_yDsRzZqj9Y1Lw6s")
                .build();

        VerifiedAddressResponse response = VerifiedAddressResponse.builder()
                .verified(true)
                .placeId("ChIJ7eXy0rP_yDsRzZqj9Y1Lw6s")
                .formattedAddress("Collector Office Road, Kurnool, Andhra Pradesh 518001")
                .latitude(15.8281)
                .longitude(78.0373)
                .city("Kurnool")
                .state("Andhra Pradesh")
                .postalCode("518001")
                .country("India")
                .countryCode("IN")
                .build();

        when(addressVerificationService.verifyPlaceId("ChIJ7eXy0rP_yDsRzZqj9Y1Lw6s")).thenReturn(response);

        mockMvc.perform(post("/api/delivery/verify-address")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.verified").value(true))
                .andExpect(jsonPath("$.data.city").value("Kurnool"));
    }

    @Test
    @DisplayName("POST /api/delivery/resolve-location reverse-geocodes coordinates")
    void testResolveLocation() throws Exception {
        ResolveLocationRequest request = ResolveLocationRequest.builder()
                .latitude(15.8281)
                .longitude(78.0373)
                .build();

        ResolvedLocationResponse location = ResolvedLocationResponse.builder()
                .verified(true)
                .placeId("ChIJ7eXy0rP_yDsRzZqj9Y1Lw6s")
                .formattedAddress("Near Collector Office, Kurnool")
                .latitude(15.8281)
                .longitude(78.0373)
                .city("Kurnool")
                .state("Andhra Pradesh")
                .country("India")
                .availableForDelivery(true)
                .distanceMeters(2350)
                .distanceKm(2.35)
                .deliveryCharge(new BigDecimal("20.00"))
                .currency("INR")
                .message("Location resolved and delivery is available")
                .build();

        when(addressVerificationService.resolveCurrentLocation(15.8281, 78.0373)).thenReturn(location);
        when(deliveryEligibilityService.enrichResolvedLocationWithDelivery(any())).thenReturn(location);

        mockMvc.perform(post("/api/delivery/resolve-location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.availableForDelivery").value(true))
                .andExpect(jsonPath("$.data.distanceKm").value(2.35));
    }

    @Test
    @DisplayName("POST /api/delivery/check-availability with Place ID returns road distance and charge")
    void testCheckAvailability_PlaceId() throws Exception {
        CheckDeliveryAvailabilityRequest request = CheckDeliveryAvailabilityRequest.builder()
                .placeId("ChIJ7eXy0rP_yDsRzZqj9Y1Lw6s")
                .build();

        DeliveryAvailabilityResponse response = DeliveryAvailabilityResponse.builder()
                .available(true)
                .verified(true)
                .distanceMeters(2350)
                .distanceKm(2.35)
                .estimatedTravelDuration("600s")
                .deliveryCharge(new BigDecimal("20.00"))
                .currency("INR")
                .maximumDeliveryDistanceKm(5.0)
                .message("Delivery is available at your location")
                .build();

        when(deliveryEligibilityService.checkAvailability(any(CheckDeliveryAvailabilityRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/delivery/check-availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.distanceMeters").value(2350))
                .andExpect(jsonPath("$.data.deliveryCharge").value(20.00));
    }

    @Test
    @DisplayName("POST /api/delivery/validate-checkout validates checkout distance and returns authoritative fee")
    void testValidateCheckout() throws Exception {
        ValidateCheckoutRequest request = ValidateCheckoutRequest.builder()
                .placeId("ChIJ7eXy0rP_yDsRzZqj9Y1Lw6s")
                .cartId("cart-12345")
                .build();

        CheckoutValidationResponse response = CheckoutValidationResponse.builder()
                .valid(true)
                .available(true)
                .verified(true)
                .cartId("cart-12345")
                .storeId("store-kurnool-primary")
                .placeId("ChIJ7eXy0rP_yDsRzZqj9Y1Lw6s")
                .deliveryAddress("Collector Office Road, Kurnool")
                .latitude(15.8281)
                .longitude(78.0373)
                .distanceMeters(2350)
                .distanceKm(2.35)
                .deliveryCharge(new BigDecimal("20.00"))
                .currency("INR")
                .message("Checkout delivery location and authoritative pricing validated successfully")
                .build();

        when(checkoutValidationService.validateCheckout(any(ValidateCheckoutRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/delivery/validate-checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.deliveryCharge").value(20.00));
    }

    @Test
    @DisplayName("GET /api/delivery/zones returns active zones (Legacy)")
    void testGetActiveZones() throws Exception {
        CustomerDeliveryZoneResponse zone = CustomerDeliveryZoneResponse.builder()
                .publicId("zone-1")
                .zoneName("Kurnool Zone 1")
                .city("Kurnool")
                .deliveryCharge(new BigDecimal("30.00"))
                .minimumOrderAmount(new BigDecimal("199.00"))
                .freeDeliveryThreshold(new BigDecimal("499.00"))
                .estimatedDeliveryTime("NEXT_DAY")
                .build();

        when(legacyDeliveryZoneService.getActiveCustomerZones()).thenReturn(List.of(zone));

        mockMvc.perform(get("/api/delivery/zones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].zoneName").value("Kurnool Zone 1"))
                .andExpect(jsonPath("$.data[0].deliveryCharge").value(30.00));
    }

    @Test
    @DisplayName("GET /api/delivery/zones/resolve resolves zone from query coordinates (Legacy)")
    void testResolveZone() throws Exception {
        DeliveryAvailabilityResponse response = DeliveryAvailabilityResponse.builder()
                .available(true)
                .zonePublicId("zone-1")
                .zoneName("Kurnool Zone 1")
                .distanceMeters(2000)
                .distanceKm(2.0)
                .deliveryCharge(new BigDecimal("15.00"))
                .build();

        when(deliveryEligibilityService.checkAvailability(any(CheckDeliveryAvailabilityRequest.class))).thenReturn(response);

        mockMvc.perform(get("/api/delivery/zones/resolve")
                        .param("latitude", "15.8281")
                        .param("longitude", "78.0373"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.zonePublicId").value("zone-1"));
    }
}
