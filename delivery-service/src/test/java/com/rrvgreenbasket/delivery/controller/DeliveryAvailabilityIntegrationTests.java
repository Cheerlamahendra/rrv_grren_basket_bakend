package com.rrvgreenbasket.delivery.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.delivery.dto.request.CheckDeliveryAvailabilityRequest;
import com.rrvgreenbasket.delivery.entity.DeliveryPricingSlab;
import com.rrvgreenbasket.delivery.entity.StoreLocation;
import com.rrvgreenbasket.delivery.repository.DeliveryPricingSlabRepository;
import com.rrvgreenbasket.delivery.repository.StoreLocationRepository;
import com.rrvgreenbasket.delivery.service.GoogleRoutesService;
import com.rrvgreenbasket.delivery.service.GoogleRoutesService.RouteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("Delivery Check Availability Integration Tests")
class DeliveryAvailabilityIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StoreLocationRepository storeLocationRepository;

    @Autowired
    private DeliveryPricingSlabRepository pricingSlabRepository;

    @MockBean
    private GoogleRoutesService googleRoutesService;

    @BeforeEach
    void setUp() {
        pricingSlabRepository.deleteAll();
        storeLocationRepository.deleteAll();

        // 1. Primary Store configuration matching delivery_db
        StoreLocation store = StoreLocation.builder()
                .publicId("store-kurnool-primary")
                .storeName("RRV Green Basket - Kurnool Central")
                .googlePlaceId("ChIJ7eXy0rP_yDsRzZqj9Y1Lw6s")
                .formattedAddress("Near Collector Office, Kurnool, Andhra Pradesh 518001, India")
                .latitude(15.8281)
                .longitude(78.0373)
                .city("Kurnool")
                .state("Andhra Pradesh")
                .postalCode("518001")
                .country("India")
                .maximumDeliveryDistanceMeters(15000)
                .deliveryEnabled(true)
                .primaryStore(true)
                .minimumOrderAmount(BigDecimal.ZERO)
                .freeDeliveryThreshold(new BigDecimal("500.00"))
                .estimatedDeliveryTime("30-45 mins")
                .currency("INR")
                .build();
        storeLocationRepository.save(store);

        // 2. All 15 active pricing slabs matching delivery_db
        List<DeliveryPricingSlab> slabs = List.of(
                DeliveryPricingSlab.builder().minimumDistanceMeters(0).maximumDistanceMeters(1000).deliveryCharge(new BigDecimal("10.00")).currency("INR").active(true).build(),
                DeliveryPricingSlab.builder().minimumDistanceMeters(1001).maximumDistanceMeters(2000).deliveryCharge(new BigDecimal("15.00")).currency("INR").active(true).build(),
                DeliveryPricingSlab.builder().minimumDistanceMeters(2001).maximumDistanceMeters(3000).deliveryCharge(new BigDecimal("20.00")).currency("INR").active(true).build(),
                DeliveryPricingSlab.builder().minimumDistanceMeters(3001).maximumDistanceMeters(4000).deliveryCharge(new BigDecimal("25.00")).currency("INR").active(true).build(),
                DeliveryPricingSlab.builder().minimumDistanceMeters(4001).maximumDistanceMeters(5000).deliveryCharge(new BigDecimal("30.00")).currency("INR").active(true).build(),
                DeliveryPricingSlab.builder().minimumDistanceMeters(5001).maximumDistanceMeters(6000).deliveryCharge(new BigDecimal("35.00")).currency("INR").active(true).build(),
                DeliveryPricingSlab.builder().minimumDistanceMeters(6001).maximumDistanceMeters(7000).deliveryCharge(new BigDecimal("40.00")).currency("INR").active(true).build(),
                DeliveryPricingSlab.builder().minimumDistanceMeters(7001).maximumDistanceMeters(8000).deliveryCharge(new BigDecimal("45.00")).currency("INR").active(true).build(),
                DeliveryPricingSlab.builder().minimumDistanceMeters(8001).maximumDistanceMeters(9000).deliveryCharge(new BigDecimal("50.00")).currency("INR").active(true).build(),
                DeliveryPricingSlab.builder().minimumDistanceMeters(9001).maximumDistanceMeters(10000).deliveryCharge(new BigDecimal("55.00")).currency("INR").active(true).build(),
                DeliveryPricingSlab.builder().minimumDistanceMeters(10001).maximumDistanceMeters(11000).deliveryCharge(new BigDecimal("60.00")).currency("INR").active(true).build(),
                DeliveryPricingSlab.builder().minimumDistanceMeters(11001).maximumDistanceMeters(12000).deliveryCharge(new BigDecimal("65.00")).currency("INR").active(true).build(),
                DeliveryPricingSlab.builder().minimumDistanceMeters(12001).maximumDistanceMeters(13000).deliveryCharge(new BigDecimal("70.00")).currency("INR").active(true).build(),
                DeliveryPricingSlab.builder().minimumDistanceMeters(13001).maximumDistanceMeters(14000).deliveryCharge(new BigDecimal("75.00")).currency("INR").active(true).build(),
                DeliveryPricingSlab.builder().minimumDistanceMeters(14001).maximumDistanceMeters(15000).deliveryCharge(new BigDecimal("80.00")).currency("INR").active(true).build()
        );
        pricingSlabRepository.saveAll(slabs);
    }

    @Test
    @DisplayName("POST /api/delivery/check-availability - Customer road distance 7093m returns delivery available with ₹45 charge")
    void testCheckAvailability_7093Meters() throws Exception {
        // Customer location resulting in road distance of 7093m
        when(googleRoutesService.computeRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new RouteResult(7093, 7.09, "1200s"));

        CheckDeliveryAvailabilityRequest request = CheckDeliveryAvailabilityRequest.builder()
                .latitude(15.8800)
                .longitude(78.0900)
                .address("Customer Address in Kurnool Outer")
                .build();

        mockMvc.perform(post("/api/delivery/check-availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.distanceMeters").value(7093))
                .andExpect(jsonPath("$.data.distanceKm").value(7.09))
                .andExpect(jsonPath("$.data.deliveryCharge").value(45.00))
                .andExpect(jsonPath("$.data.currency").value("INR"))
                .andExpect(jsonPath("$.data.maximumDeliveryDistanceKm").value(15.0))
                .andExpect(jsonPath("$.data.zoneName").value("RRV Green Basket - Kurnool Central"))
                .andExpect(jsonPath("$.data.message").value("Delivery is available at your location"));
    }

    @Test
    @DisplayName("POST /api/delivery/check-availability - Customer road distance 7207m returns delivery available with ₹45 charge")
    void testCheckAvailability_7207Meters() throws Exception {
        when(googleRoutesService.computeRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new RouteResult(7207, 7.21, "1260s"));

        CheckDeliveryAvailabilityRequest request = CheckDeliveryAvailabilityRequest.builder()
                .latitude(15.8850)
                .longitude(78.0950)
                .address("Customer Address in Kurnool Outer")
                .build();

        mockMvc.perform(post("/api/delivery/check-availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.distanceMeters").value(7207))
                .andExpect(jsonPath("$.data.distanceKm").value(7.21))
                .andExpect(jsonPath("$.data.deliveryCharge").value(45.00))
                .andExpect(jsonPath("$.data.currency").value("INR"))
                .andExpect(jsonPath("$.data.maximumDeliveryDistanceKm").value(15.0));
    }
}
