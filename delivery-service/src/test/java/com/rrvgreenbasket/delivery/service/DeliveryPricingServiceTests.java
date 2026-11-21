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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
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
                DeliveryPricingSlab.builder().id(5L).minimumDistanceMeters(4001).maximumDistanceMeters(5000).deliveryCharge(new BigDecimal("30.00")).currency("INR").active(true).build(),
                DeliveryPricingSlab.builder().id(6L).minimumDistanceMeters(5001).maximumDistanceMeters(6000).deliveryCharge(new BigDecimal("35.00")).currency("INR").active(true).build(),
                DeliveryPricingSlab.builder().id(7L).minimumDistanceMeters(6001).maximumDistanceMeters(7000).deliveryCharge(new BigDecimal("40.00")).currency("INR").active(true).build(),
                DeliveryPricingSlab.builder().id(8L).minimumDistanceMeters(7001).maximumDistanceMeters(8000).deliveryCharge(new BigDecimal("45.00")).currency("INR").active(true).build(),
                DeliveryPricingSlab.builder().id(9L).minimumDistanceMeters(8001).maximumDistanceMeters(9000).deliveryCharge(new BigDecimal("50.00")).currency("INR").active(true).build(),
                DeliveryPricingSlab.builder().id(10L).minimumDistanceMeters(9001).maximumDistanceMeters(10000).deliveryCharge(new BigDecimal("55.00")).currency("INR").active(true).build(),
                DeliveryPricingSlab.builder().id(11L).minimumDistanceMeters(10001).maximumDistanceMeters(11000).deliveryCharge(new BigDecimal("60.00")).currency("INR").active(true).build(),
                DeliveryPricingSlab.builder().id(12L).minimumDistanceMeters(11001).maximumDistanceMeters(12000).deliveryCharge(new BigDecimal("65.00")).currency("INR").active(true).build(),
                DeliveryPricingSlab.builder().id(13L).minimumDistanceMeters(12001).maximumDistanceMeters(13000).deliveryCharge(new BigDecimal("70.00")).currency("INR").active(true).build(),
                DeliveryPricingSlab.builder().id(14L).minimumDistanceMeters(13001).maximumDistanceMeters(14000).deliveryCharge(new BigDecimal("75.00")).currency("INR").active(true).build(),
                DeliveryPricingSlab.builder().id(15L).minimumDistanceMeters(14001).maximumDistanceMeters(15000).deliveryCharge(new BigDecimal("80.00")).currency("INR").active(true).build()
        );
    }

    @ParameterizedTest(name = "Distance {0}m should produce delivery charge ₹{1}")
    @CsvSource({
            "0, 10.00",
            "1000, 10.00",
            "1001, 15.00",
            "5000, 30.00",
            "5001, 35.00",
            "6000, 35.00",
            "6001, 40.00",
            "6906, 40.00",
            "7000, 40.00",
            "7001, 45.00",
            "7093, 45.00",
            "7207, 45.00",
            "8000, 45.00",
            "8001, 50.00",
            "15000, 80.00"
    })
    @DisplayName("Pricing calculation - Mandatory distance test matrix")
    void testMandatoryDistanceTestMatrix(int distanceMeters, String expectedCharge) {
        when(pricingSlabRepository.findByActiveTrueOrderByMinimumDistanceMetersAsc()).thenReturn(sampleSlabs);

        BigDecimal charge = pricingService.calculateDeliveryCharge(distanceMeters);
        assertThat(charge).isEqualByComparingTo(new BigDecimal(expectedCharge));
    }

    @Test
    @DisplayName("Pricing calculation - Direct repository lookup via findActiveSlabsForDistance")
    void testDirectRepositoryLookup() {
        DeliveryPricingSlab slab8 = sampleSlabs.get(7); // 7001 - 8000 m -> ₹45.00
        when(pricingSlabRepository.findActiveSlabsForDistance(eq(7093), any(LocalDateTime.class)))
                .thenReturn(List.of(slab8));

        BigDecimal charge = pricingService.calculateDeliveryCharge(7093);
        assertThat(charge).isEqualByComparingTo(new BigDecimal("45.00"));
        verify(pricingSlabRepository).findActiveSlabsForDistance(eq(7093), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("Pricing calculation - 7207 meters lookup via findActiveSlabsForDistance")
    void test7207MetersDirectLookup() {
        DeliveryPricingSlab slab8 = sampleSlabs.get(7); // 7001 - 8000 m -> ₹45.00
        when(pricingSlabRepository.findActiveSlabsForDistance(eq(7207), any(LocalDateTime.class)))
                .thenReturn(List.of(slab8));

        BigDecimal charge = pricingService.calculateDeliveryCharge(7207);
        assertThat(charge).isEqualByComparingTo(new BigDecimal("45.00"));
    }

    @Test
    @DisplayName("Pricing calculation - Inactive slabs are ignored")
    void testInactiveSlabsIgnored() {
        DeliveryPricingSlab inactiveSlab = DeliveryPricingSlab.builder()
                .id(99L)
                .minimumDistanceMeters(7001)
                .maximumDistanceMeters(8000)
                .deliveryCharge(new BigDecimal("45.00"))
                .currency("INR")
                .active(false)
                .build();

        // Repository returns empty list for distance because slab is inactive
        when(pricingSlabRepository.findActiveSlabsForDistance(eq(7093), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(pricingSlabRepository.findByActiveTrueOrderByMinimumDistanceMetersAsc())
                .thenReturn(List.of(inactiveSlab));

        assertThatThrownBy(() -> pricingService.calculateDeliveryCharge(7093))
                .isInstanceOf(DeliveryPricingNotConfiguredException.class)
                .hasMessageContaining("No delivery pricing slab configured for road distance of 7093 meters");
    }

    @Test
    @DisplayName("Pricing calculation - Distance below zero is rejected")
    void testNegativeDistanceRejected() {
        assertThatThrownBy(() -> pricingService.calculateDeliveryCharge(-1))
                .isInstanceOf(InvalidDeliveryConfigurationException.class)
                .hasMessageContaining("Road distance cannot be negative");

        assertThatThrownBy(() -> pricingService.calculateDeliveryCharge(-500))
                .isInstanceOf(InvalidDeliveryConfigurationException.class)
                .hasMessageContaining("Road distance cannot be negative");
    }

    @Test
    @DisplayName("Pricing calculation - Distance beyond configured slabs throws exception")
    void testDistanceBeyondConfiguredSlabsThrows() {
        when(pricingSlabRepository.findByActiveTrueOrderByMinimumDistanceMetersAsc()).thenReturn(sampleSlabs);

        assertThatThrownBy(() -> pricingService.calculateDeliveryCharge(15001))
                .isInstanceOf(DeliveryPricingNotConfiguredException.class)
                .hasMessageContaining("No delivery pricing slab configured for road distance of 15001 meters");
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
    @DisplayName("Pricing calculation - NULL effective dates are accepted as unrestricted")
    void testNullEffectiveDatesAccepted() {
        DeliveryPricingSlab nullDatesSlab = DeliveryPricingSlab.builder()
                .id(8L)
                .minimumDistanceMeters(7001)
                .maximumDistanceMeters(8000)
                .deliveryCharge(new BigDecimal("45.00"))
                .currency("INR")
                .active(true)
                .effectiveStartDate(null)
                .effectiveEndDate(null)
                .build();

        assertThat(nullDatesSlab.covers(7093)).isTrue();
        assertThat(nullDatesSlab.covers(7093, LocalDateTime.now())).isTrue();
    }

    @Test
    @DisplayName("Pricing calculation - Future effective start date is not active yet")
    void testFutureEffectiveStartDateRejected() {
        DeliveryPricingSlab futureSlab = DeliveryPricingSlab.builder()
                .id(8L)
                .minimumDistanceMeters(7001)
                .maximumDistanceMeters(8000)
                .deliveryCharge(new BigDecimal("45.00"))
                .currency("INR")
                .active(true)
                .effectiveStartDate(LocalDateTime.now().plusDays(5))
                .effectiveEndDate(null)
                .build();

        assertThat(futureSlab.covers(7093)).isFalse();
    }

    @Test
    @DisplayName("Pricing calculation - Past effective end date is expired")
    void testPastEffectiveEndDateRejected() {
        DeliveryPricingSlab expiredSlab = DeliveryPricingSlab.builder()
                .id(8L)
                .minimumDistanceMeters(7001)
                .maximumDistanceMeters(8000)
                .deliveryCharge(new BigDecimal("45.00"))
                .currency("INR")
                .active(true)
                .effectiveStartDate(null)
                .effectiveEndDate(LocalDateTime.now().minusDays(1))
                .build();

        assertThat(expiredSlab.covers(7093)).isFalse();
    }

    @Test
    @DisplayName("Pricing calculation - Overlapping active slabs reject silent arbitrary selection")
    void testOverlappingActiveSlabsThrows() {
        DeliveryPricingSlab slabA = DeliveryPricingSlab.builder().id(1L).minimumDistanceMeters(7000).maximumDistanceMeters(8000).deliveryCharge(new BigDecimal("45.00")).active(true).build();
        DeliveryPricingSlab slabB = DeliveryPricingSlab.builder().id(2L).minimumDistanceMeters(7050).maximumDistanceMeters(8050).deliveryCharge(new BigDecimal("48.00")).active(true).build();

        when(pricingSlabRepository.findActiveSlabsForDistance(eq(7093), any(LocalDateTime.class)))
                .thenReturn(List.of(slabA, slabB));

        assertThatThrownBy(() -> pricingService.calculateDeliveryCharge(7093))
                .isInstanceOf(InvalidDeliveryConfigurationException.class)
                .hasMessageContaining("Multiple overlapping active delivery pricing slabs found");
    }

    @Test
    @DisplayName("Admin validation - Overlapping slabs are rejected on create")
    void testOverlappingSlabsRejectedOnCreate() {
        when(pricingSlabRepository.findByActiveTrueOrderByMinimumDistanceMetersAsc()).thenReturn(sampleSlabs);

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
                .minimumDistanceMeters(15001)
                .maximumDistanceMeters(16000)
                .deliveryCharge(new BigDecimal("85.00"))
                .currency("INR")
                .active(true)
                .build();

        DeliveryPricingSlab savedSlab = DeliveryPricingSlab.builder()
                .id(16L)
                .minimumDistanceMeters(15001)
                .maximumDistanceMeters(16000)
                .deliveryCharge(new BigDecimal("85.00"))
                .currency("INR")
                .active(true)
                .build();

        when(pricingSlabRepository.save(any(DeliveryPricingSlab.class))).thenReturn(savedSlab);

        DeliveryPricingResponse response = pricingService.createPricingSlab(validRequest, "admin-1");
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(16L);
        assertThat(response.getDeliveryCharge()).isEqualByComparingTo(new BigDecimal("85.00"));
        assertThat(response.getMinimumDistanceKm()).isEqualTo(15.0);
        assertThat(response.getMaximumDistanceKm()).isEqualTo(16.0);
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
