package com.rrvgreenbasket.delivery.service;

import com.rrvgreenbasket.delivery.dto.request.DeliveryPricingRequest;
import com.rrvgreenbasket.delivery.dto.response.DeliveryPricingResponse;
import com.rrvgreenbasket.delivery.entity.DeliveryPricingSlab;
import com.rrvgreenbasket.delivery.exception.DeliveryPricingNotConfiguredException;
import com.rrvgreenbasket.delivery.exception.InvalidDeliveryConfigurationException;
import com.rrvgreenbasket.delivery.repository.DeliveryPricingSlabRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeliveryPricingService Unit Tests")
class DeliveryPricingServiceTests {

    @Mock
    private DeliveryPricingSlabRepository pricingSlabRepository;

    @InjectMocks
    private DeliveryPricingService pricingService;

    private List<DeliveryPricingSlab> sampleSlabs;

    @BeforeEach
    void setUp() {
        sampleSlabs = List.of(
                DeliveryPricingSlab.builder().id(1L).minimumDistanceMeters(0).maximumDistanceMeters(1000).deliveryCharge(new BigDecimal("10.00")).currency("INR").active(true).build(),
                DeliveryPricingSlab.builder().id(2L).minimumDistanceMeters(1001).maximumDistanceMeters(2000).deliveryCharge(new BigDecimal("15.00")).currency("INR").active(true).build(),
                DeliveryPricingSlab.builder().id(3L).minimumDistanceMeters(2001).maximumDistanceMeters(3000).deliveryCharge(new BigDecimal("20.00")).currency("INR").active(true).build(),
                DeliveryPricingSlab.builder().id(4L).minimumDistanceMeters(3001).maximumDistanceMeters(4000).deliveryCharge(new BigDecimal("25.00")).currency("INR").active(true).build(),
                DeliveryPricingSlab.builder().id(5L).minimumDistanceMeters(4001).maximumDistanceMeters(5000).deliveryCharge(new BigDecimal("30.00")).currency("INR").active(true).build()
        );
    }

    @Test
    @DisplayName("Pricing calculation - Correct first slab (500m -> ₹10)")
    void testFirstSlab() {
        when(pricingSlabRepository.findByActiveTrueOrderByMinimumDistanceMetersAsc()).thenReturn(sampleSlabs);

        BigDecimal charge = pricingService.calculateDeliveryCharge(500);
        assertThat(charge).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    @DisplayName("Pricing calculation - Correct second slab (1500m -> ₹15)")
    void testSecondSlab() {
        when(pricingSlabRepository.findByActiveTrueOrderByMinimumDistanceMetersAsc()).thenReturn(sampleSlabs);

        BigDecimal charge = pricingService.calculateDeliveryCharge(1500);
        assertThat(charge).isEqualByComparingTo(new BigDecimal("15.00"));
    }

    @Test
    @DisplayName("Pricing calculation - Correct third slab (2350m -> ₹20)")
    void testThirdSlab() {
        when(pricingSlabRepository.findByActiveTrueOrderByMinimumDistanceMetersAsc()).thenReturn(sampleSlabs);

        BigDecimal charge = pricingService.calculateDeliveryCharge(2350);
        assertThat(charge).isEqualByComparingTo(new BigDecimal("20.00"));
    }

    @Test
    @DisplayName("Pricing calculation - Correct fourth slab (3500m -> ₹25)")
    void testFourthSlab() {
        when(pricingSlabRepository.findByActiveTrueOrderByMinimumDistanceMetersAsc()).thenReturn(sampleSlabs);

        BigDecimal charge = pricingService.calculateDeliveryCharge(3500);
        assertThat(charge).isEqualByComparingTo(new BigDecimal("25.00"));
    }

    @Test
    @DisplayName("Pricing calculation - Correct fifth slab (4500m -> ₹30)")
    void testFifthSlab() {
        when(pricingSlabRepository.findByActiveTrueOrderByMinimumDistanceMetersAsc()).thenReturn(sampleSlabs);

        BigDecimal charge = pricingService.calculateDeliveryCharge(4500);
        assertThat(charge).isEqualByComparingTo(new BigDecimal("30.00"));
    }

    @Test
    @DisplayName("Pricing calculation - Exact slab boundaries (0m, 1000m, 1001m, 5000m)")
    void testExactBoundaries() {
        when(pricingSlabRepository.findByActiveTrueOrderByMinimumDistanceMetersAsc()).thenReturn(sampleSlabs);

        assertThat(pricingService.calculateDeliveryCharge(0)).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(pricingService.calculateDeliveryCharge(1000)).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(pricingService.calculateDeliveryCharge(1001)).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(pricingService.calculateDeliveryCharge(2000)).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(pricingService.calculateDeliveryCharge(5000)).isEqualByComparingTo(new BigDecimal("30.00"));
    }

    @Test
    @DisplayName("Pricing calculation - Distance beyond configured slabs throws exception")
    void testDistanceBeyondConfiguredSlabsThrows() {
        when(pricingSlabRepository.findByActiveTrueOrderByMinimumDistanceMetersAsc()).thenReturn(sampleSlabs);

        assertThatThrownBy(() -> pricingService.calculateDeliveryCharge(5001))
                .isInstanceOf(DeliveryPricingNotConfiguredException.class)
                .hasMessageContaining("No delivery pricing slab configured for road distance of 5001 meters");
    }

    @Test
    @DisplayName("Pricing calculation - Empty pricing configuration throws DeliveryPricingNotConfiguredException")
    void testEmptyPricingConfigThrows() {
        when(pricingSlabRepository.findByActiveTrueOrderByMinimumDistanceMetersAsc()).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> pricingService.calculateDeliveryCharge(1000))
                .isInstanceOf(DeliveryPricingNotConfiguredException.class)
                .hasMessageContaining("Delivery pricing configuration is missing");
    }

    @Test
    @DisplayName("Admin validation - Overlapping slabs are rejected")
    void testOverlappingSlabsRejected() {
        when(pricingSlabRepository.findByActiveTrueOrderByMinimumDistanceMetersAsc()).thenReturn(sampleSlabs);

        // Try to add slab [500 - 1500 m], which overlaps with [0-1000] and [1001-2000]
        DeliveryPricingRequest overlappingRequest = DeliveryPricingRequest.builder()
                .minimumDistanceMeters(500)
                .maximumDistanceMeters(1500)
                .deliveryCharge(new BigDecimal("12.00"))
                .currency("INR")
                .active(true)
                .build();

        assertThatThrownBy(() -> pricingService.createPricingSlab(overlappingRequest, "admin-1"))
                .isInstanceOf(InvalidDeliveryConfigurationException.class)
                .hasMessageContaining("overlaps with existing active slab");
    }

    @Test
    @DisplayName("Admin validation - Negative distance or negative price is rejected")
    void testNegativePriceOrDistanceRejected() {
        DeliveryPricingRequest negativeDist = DeliveryPricingRequest.builder()
                .minimumDistanceMeters(-10)
                .maximumDistanceMeters(500)
                .deliveryCharge(new BigDecimal("10.00"))
                .build();

        assertThatThrownBy(() -> pricingService.createPricingSlab(negativeDist, "admin-1"))
                .isInstanceOf(InvalidDeliveryConfigurationException.class);

        DeliveryPricingRequest minGreaterThanMax = DeliveryPricingRequest.builder()
                .minimumDistanceMeters(1000)
                .maximumDistanceMeters(500)
                .deliveryCharge(new BigDecimal("10.00"))
                .build();

        assertThatThrownBy(() -> pricingService.createPricingSlab(minGreaterThanMax, "admin-1"))
                .isInstanceOf(InvalidDeliveryConfigurationException.class);

        DeliveryPricingRequest negativeFee = DeliveryPricingRequest.builder()
                .minimumDistanceMeters(5001)
                .maximumDistanceMeters(6000)
                .deliveryCharge(new BigDecimal("-5.00"))
                .build();

        assertThatThrownBy(() -> pricingService.createPricingSlab(negativeFee, "admin-1"))
                .isInstanceOf(InvalidDeliveryConfigurationException.class);
    }

    @Test
    @DisplayName("Admin operation - Pricing slab creation success")
    void testCreatePricingSlabSuccess() {
        when(pricingSlabRepository.findByActiveTrueOrderByMinimumDistanceMetersAsc()).thenReturn(sampleSlabs);

        DeliveryPricingRequest validRequest = DeliveryPricingRequest.builder()
                .minimumDistanceMeters(5001)
                .maximumDistanceMeters(6000)
                .deliveryCharge(new BigDecimal("35.00"))
                .currency("INR")
                .active(true)
                .build();

        DeliveryPricingSlab savedSlab = DeliveryPricingSlab.builder()
                .id(6L)
                .minimumDistanceMeters(5001)
                .maximumDistanceMeters(6000)
                .deliveryCharge(new BigDecimal("35.00"))
                .currency("INR")
                .active(true)
                .build();

        when(pricingSlabRepository.save(any(DeliveryPricingSlab.class))).thenReturn(savedSlab);

        DeliveryPricingResponse response = pricingService.createPricingSlab(validRequest, "admin-1");
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(6L);
        assertThat(response.getDeliveryCharge()).isEqualByComparingTo(new BigDecimal("35.00"));
        assertThat(response.getMinimumDistanceKm()).isEqualTo(5.0);
        assertThat(response.getMaximumDistanceKm()).isEqualTo(6.0);
    }

    @Test
    @DisplayName("Admin operation - Deactivate pricing slab")
    void testDeactivatePricingSlab() {
        DeliveryPricingSlab slab = DeliveryPricingSlab.builder()
                .id(1L)
                .minimumDistanceMeters(0)
                .maximumDistanceMeters(1000)
                .deliveryCharge(new BigDecimal("10.00"))
                .active(true)
                .build();

        when(pricingSlabRepository.findById(1L)).thenReturn(Optional.of(slab));
        when(pricingSlabRepository.save(any(DeliveryPricingSlab.class))).thenReturn(slab);

        pricingService.deleteOrDeactivatePricingSlab(1L, "admin-1");

        assertThat(slab.getActive()).isFalse();
        assertThat(slab.getUpdatedBy()).isEqualTo("admin-1");
        verify(pricingSlabRepository).save(slab);
    }
}
