package com.rrvgreenbasket.delivery.service;

import com.rrvgreenbasket.delivery.dto.request.ValidateCheckoutRequest;
import com.rrvgreenbasket.delivery.dto.response.CheckoutValidationResponse;
import com.rrvgreenbasket.delivery.dto.response.VerifiedAddressResponse;
import com.rrvgreenbasket.delivery.entity.DeliveryPricingSlab;
import com.rrvgreenbasket.delivery.entity.StoreLocation;
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
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Delivery Checkout Validation Unit Tests")
class CheckoutValidationIntegrationTests {

    @Mock
    private StoreLocationService storeLocationService;

    @Mock
    private GoogleRoutesService googleRoutesService;

    @Mock
    private DeliveryPricingService deliveryPricingService;

    @Mock
    private AddressVerificationService addressVerificationService;

    @InjectMocks
    private DeliveryCheckoutValidationService checkoutService;

    private StoreLocation store;

    @BeforeEach
    void setUp() {
        store = StoreLocation.builder()
                .publicId("store-kurnool-primary")
                .storeName("RRV Green Basket - Kurnool Central")
                .latitude(15.8281)
                .longitude(78.0373)
                .maximumDeliveryDistanceMeters(5000)
                .deliveryEnabled(true)
                .minimumOrderAmount(BigDecimal.ZERO)
                .freeDeliveryThreshold(new BigDecimal("500.00"))
                .currency("INR")
                .build();
    }

    @Test
    @DisplayName("Checkout Validation - Within 5 km limit returns valid with authoritative delivery fee")
    void testCheckoutWithinLimit() {
        when(storeLocationService.getPrimaryStore()).thenReturn(store);
        when(addressVerificationService.verifyPlaceId("ChIJ7eXy0rP_yDsRzZqj9Y1Lw6s"))
                .thenReturn(VerifiedAddressResponse.builder()
                        .verified(true)
                        .placeId("ChIJ7eXy0rP_yDsRzZqj9Y1Lw6s")
                        .formattedAddress("Near Collector Office, Kurnool")
                        .latitude(15.8300)
                        .longitude(78.0400)
                        .build());
        when(googleRoutesService.computeRoute(15.8281, 78.0373, 15.8300, 78.0400))
                .thenReturn(new RouteResult(2350, 2.35, "600s"));

        DeliveryPricingSlab slab = DeliveryPricingSlab.builder().id(3L).build();
        when(deliveryPricingService.findSlabForDistance(2350)).thenReturn(slab);
        when(deliveryPricingService.calculateDeliveryCharge(2350)).thenReturn(new BigDecimal("20.00"));

        ValidateCheckoutRequest request = ValidateCheckoutRequest.builder()
                .placeId("ChIJ7eXy0rP_yDsRzZqj9Y1Lw6s")
                .cartId("cart-999")
                .build();

        CheckoutValidationResponse response = checkoutService.validateCheckout(request);

        assertThat(response.isValid()).isTrue();
        assertThat(response.isAvailable()).isTrue();
        assertThat(response.isVerified()).isTrue();
        assertThat(response.getDistanceMeters()).isEqualTo(2350);
        assertThat(response.getDistanceKm()).isEqualTo(2.35);
        assertThat(response.getDeliveryCharge()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(response.getPricingSlabId()).isEqualTo(3L);
        assertThat(response.getStoreId()).isEqualTo("store-kurnool-primary");
    }

    @Test
    @DisplayName("Checkout Validation - Beyond 5 km limit returns invalid and unavailable")
    void testCheckoutBeyondLimit() {
        when(storeLocationService.getPrimaryStore()).thenReturn(store);
        when(addressVerificationService.verifyPlaceId("place-outside"))
                .thenReturn(VerifiedAddressResponse.builder()
                        .verified(true)
                        .placeId("place-outside")
                        .formattedAddress("Distant Village, Kurnool District")
                        .latitude(15.9500)
                        .longitude(78.2000)
                        .build());
        when(googleRoutesService.computeRoute(15.8281, 78.0373, 15.9500, 78.2000))
                .thenReturn(new RouteResult(6200, 6.20, "1500s"));

        ValidateCheckoutRequest request = ValidateCheckoutRequest.builder()
                .placeId("place-outside")
                .cartId("cart-999")
                .build();

        CheckoutValidationResponse response = checkoutService.validateCheckout(request);

        assertThat(response.isValid()).isFalse();
        assertThat(response.isAvailable()).isFalse();
        assertThat(response.getDistanceMeters()).isEqualTo(6200);
        assertThat(response.getDistanceKm()).isEqualTo(6.20);
        assertThat(response.getMessage()).contains("outside our maximum serviceable range of 5.0 km");
        verify(deliveryPricingService, never()).calculateDeliveryCharge(anyInt());
    }

    @Test
    @DisplayName("Checkout Validation - Delivery disabled returns invalid")
    void testCheckoutDisabledDelivery() {
        store.setDeliveryEnabled(false);
        when(storeLocationService.getPrimaryStore()).thenReturn(store);
        when(addressVerificationService.verifyPlaceId("place-1"))
                .thenReturn(VerifiedAddressResponse.builder()
                        .verified(true)
                        .latitude(15.8300)
                        .longitude(78.0400)
                        .build());

        ValidateCheckoutRequest request = ValidateCheckoutRequest.builder()
                .placeId("place-1")
                .cartId("cart-999")
                .build();

        CheckoutValidationResponse response = checkoutService.validateCheckout(request);

        assertThat(response.isValid()).isFalse();
        assertThat(response.isAvailable()).isFalse();
        assertThat(response.getMessage()).contains("disabled by store management");
        verify(googleRoutesService, never()).computeRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }
}
