package com.rrvgreenbasket.delivery.service;

import com.rrvgreenbasket.delivery.dto.request.CheckDeliveryAvailabilityRequest;
import com.rrvgreenbasket.delivery.dto.response.DeliveryAvailabilityResponse;
import com.rrvgreenbasket.delivery.dto.response.VerifiedAddressResponse;
import com.rrvgreenbasket.delivery.entity.StoreLocation;
import com.rrvgreenbasket.delivery.exception.RouteNotFoundException;
import com.rrvgreenbasket.delivery.exception.StoreLocationNotConfiguredException;
import com.rrvgreenbasket.delivery.service.GoogleRoutesService.RouteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Delivery Eligibility & 5 km Geofencing Tests")
class DeliveryEligibilityAndGeofencingTests {

    @Mock
    private StoreLocationService storeLocationService;

    @Mock
    private GoogleRoutesService googleRoutesService;

    @Mock
    private DeliveryPricingService deliveryPricingService;

    @Mock
    private AddressVerificationService addressVerificationService;

    @InjectMocks
    private DeliveryEligibilityService eligibilityService;

    private StoreLocation primaryStore;

    @BeforeEach
    void setUp() {
        primaryStore = StoreLocation.builder()
                .publicId("store-kurnool-primary")
                .storeName("RRV Green Basket - Kurnool Central")
                .latitude(15.8281)
                .longitude(78.0373)
                .maximumDeliveryDistanceMeters(5000)
                .deliveryEnabled(true)
                .primaryStore(true)
                .minimumOrderAmount(BigDecimal.ZERO)
                .freeDeliveryThreshold(new BigDecimal("500.00"))
                .estimatedDeliveryTime("30-45 mins")
                .currency("INR")
                .build();
    }

    @Test
    @DisplayName("Eligibility - 500-meter road distance is eligible (Charge ₹10)")
    void test500MetersEligible() {
        when(storeLocationService.getPrimaryStore()).thenReturn(primaryStore);
        when(googleRoutesService.computeRoute(15.8281, 78.0373, 15.8300, 78.0400))
                .thenReturn(new RouteResult(500, 0.50, "180s"));
        when(deliveryPricingService.calculateDeliveryCharge(500)).thenReturn(new BigDecimal("10.00"));

        CheckDeliveryAvailabilityRequest request = CheckDeliveryAvailabilityRequest.builder()
                .latitude(15.8300)
                .longitude(78.0400)
                .build();

        DeliveryAvailabilityResponse response = eligibilityService.checkAvailability(request);

        assertThat(response.isAvailable()).isTrue();
        assertThat(response.getDistanceMeters()).isEqualTo(500);
        assertThat(response.getDistanceKm()).isEqualTo(0.50);
        assertThat(response.getDeliveryCharge()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(response.getMaximumDeliveryDistanceKm()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("Eligibility - 1-kilometer road distance is eligible (Charge ₹10)")
    void test1000MetersEligible() {
        when(storeLocationService.getPrimaryStore()).thenReturn(primaryStore);
        when(googleRoutesService.computeRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new RouteResult(1000, 1.00, "300s"));
        when(deliveryPricingService.calculateDeliveryCharge(1000)).thenReturn(new BigDecimal("10.00"));

        CheckDeliveryAvailabilityRequest request = CheckDeliveryAvailabilityRequest.builder()
                .latitude(15.8350)
                .longitude(78.0450)
                .build();

        DeliveryAvailabilityResponse response = eligibilityService.checkAvailability(request);

        assertThat(response.isAvailable()).isTrue();
        assertThat(response.getDistanceMeters()).isEqualTo(1000);
        assertThat(response.getDeliveryCharge()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    @DisplayName("Eligibility - 2.35-kilometer road distance is eligible (Charge ₹20)")
    void test2350MetersEligible() {
        when(storeLocationService.getPrimaryStore()).thenReturn(primaryStore);
        when(googleRoutesService.computeRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new RouteResult(2350, 2.35, "600s"));
        when(deliveryPricingService.calculateDeliveryCharge(2350)).thenReturn(new BigDecimal("20.00"));

        CheckDeliveryAvailabilityRequest request = CheckDeliveryAvailabilityRequest.builder()
                .latitude(15.8450)
                .longitude(78.0550)
                .build();

        DeliveryAvailabilityResponse response = eligibilityService.checkAvailability(request);

        assertThat(response.isAvailable()).isTrue();
        assertThat(response.getDistanceMeters()).isEqualTo(2350);
        assertThat(response.getDistanceKm()).isEqualTo(2.35);
        assertThat(response.getEstimatedTravelDuration()).isEqualTo("600s");
        assertThat(response.getDeliveryCharge()).isEqualByComparingTo(new BigDecimal("20.00"));
    }

    @Test
    @DisplayName("Eligibility - Exactly 5,000 meters road distance is eligible (Boundary)")
    void testExactly5000MetersEligible() {
        when(storeLocationService.getPrimaryStore()).thenReturn(primaryStore);
        when(googleRoutesService.computeRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new RouteResult(5000, 5.00, "1200s"));
        when(deliveryPricingService.calculateDeliveryCharge(5000)).thenReturn(new BigDecimal("30.00"));

        CheckDeliveryAvailabilityRequest request = CheckDeliveryAvailabilityRequest.builder()
                .latitude(15.8700)
                .longitude(78.0800)
                .build();

        DeliveryAvailabilityResponse response = eligibilityService.checkAvailability(request);

        assertThat(response.isAvailable()).isTrue();
        assertThat(response.getDistanceMeters()).isEqualTo(5000);
        assertThat(response.getDeliveryCharge()).isEqualByComparingTo(new BigDecimal("30.00"));
    }

    @Test
    @DisplayName("Eligibility - 5,001 meters road distance is INELIGIBLE (Over 5 km limit)")
    void test5001MetersIneligible() {
        when(storeLocationService.getPrimaryStore()).thenReturn(primaryStore);
        when(googleRoutesService.computeRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new RouteResult(5001, 5.00, "1205s"));

        CheckDeliveryAvailabilityRequest request = CheckDeliveryAvailabilityRequest.builder()
                .latitude(15.8701)
                .longitude(78.0801)
                .build();

        DeliveryAvailabilityResponse response = eligibilityService.checkAvailability(request);

        assertThat(response.isAvailable()).isFalse();
        assertThat(response.getDistanceMeters()).isEqualTo(5001);
        assertThat(response.getDeliveryCharge()).isNull();
        assertThat(response.getMessage()).contains("currently deliver only within 5.0 km");
        verify(deliveryPricingService, never()).calculateDeliveryCharge(anyInt());
    }

    @Test
    @DisplayName("Eligibility - Place ID verification workflow followed by eligibility check")
    void testPlaceIdEligibilityWorkflow() {
        when(storeLocationService.getPrimaryStore()).thenReturn(primaryStore);
        when(addressVerificationService.verifyPlaceId("ChIJ7eXy0rP_yDsRzZqj9Y1Lw6s"))
                .thenReturn(VerifiedAddressResponse.builder()
                        .verified(true)
                        .placeId("ChIJ7eXy0rP_yDsRzZqj9Y1Lw6s")
                        .formattedAddress("Near Collector Office, Kurnool")
                        .latitude(15.8300)
                        .longitude(78.0400)
                        .country("India")
                        .build());
        when(googleRoutesService.computeRoute(15.8281, 78.0373, 15.8300, 78.0400))
                .thenReturn(new RouteResult(1500, 1.50, "400s"));
        when(deliveryPricingService.calculateDeliveryCharge(1500)).thenReturn(new BigDecimal("15.00"));

        CheckDeliveryAvailabilityRequest request = CheckDeliveryAvailabilityRequest.builder()
                .placeId("ChIJ7eXy0rP_yDsRzZqj9Y1Lw6s")
                .build();

        DeliveryAvailabilityResponse response = eligibilityService.checkAvailability(request);

        assertThat(response.isAvailable()).isTrue();
        assertThat(response.isVerified()).isTrue();
        assertThat(response.getDistanceMeters()).isEqualTo(1500);
        assertThat(response.getDeliveryCharge()).isEqualByComparingTo(new BigDecimal("15.00"));
    }

    @Test
    @DisplayName("Eligibility - Route unavailable throws RouteNotFoundException")
    void testRouteUnavailableThrows() {
        when(storeLocationService.getPrimaryStore()).thenReturn(primaryStore);
        when(googleRoutesService.computeRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new RouteNotFoundException("No driving route found between store and customer location"));

        CheckDeliveryAvailabilityRequest request = CheckDeliveryAvailabilityRequest.builder()
                .latitude(15.8300)
                .longitude(78.0400)
                .build();

        assertThatThrownBy(() -> eligibilityService.checkAvailability(request))
                .isInstanceOf(RouteNotFoundException.class)
                .hasMessageContaining("No driving route found");
    }

    @Test
    @DisplayName("Eligibility - Delivery disabled globally for store returns unavailable")
    void testDeliveryDisabledReturnsUnavailable() {
        primaryStore.setDeliveryEnabled(false);
        when(storeLocationService.getPrimaryStore()).thenReturn(primaryStore);

        CheckDeliveryAvailabilityRequest request = CheckDeliveryAvailabilityRequest.builder()
                .latitude(15.8300)
                .longitude(78.0400)
                .build();

        DeliveryAvailabilityResponse response = eligibilityService.checkAvailability(request);

        assertThat(response.isAvailable()).isFalse();
        assertThat(response.getMessage()).contains("currently not accepting new orders");
        verify(googleRoutesService, never()).computeRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("Eligibility - Missing store configuration throws StoreLocationNotConfiguredException")
    void testMissingStoreThrows() {
        when(storeLocationService.getPrimaryStore())
                .thenThrow(new StoreLocationNotConfiguredException("Primary store location is not configured"));

        CheckDeliveryAvailabilityRequest request = CheckDeliveryAvailabilityRequest.builder()
                .latitude(15.8300)
                .longitude(78.0400)
                .build();

        assertThatThrownBy(() -> eligibilityService.checkAvailability(request))
                .isInstanceOf(StoreLocationNotConfiguredException.class);
    }
}
