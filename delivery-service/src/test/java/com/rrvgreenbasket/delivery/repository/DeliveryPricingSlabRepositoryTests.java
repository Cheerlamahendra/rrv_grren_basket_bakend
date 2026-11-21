package com.rrvgreenbasket.delivery.repository;

import com.rrvgreenbasket.delivery.entity.DeliveryPricingSlab;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("DeliveryPricingSlabRepository JPA Integration Tests")
class DeliveryPricingSlabRepositoryTests {

    @Autowired
    private DeliveryPricingSlabRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();

        // Seed 15 active slabs exactly matching the database
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
        repository.saveAll(slabs);
    }

    @ParameterizedTest(name = "Repository lookup for {0}m returns charge ₹{1}")
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
    @DisplayName("JPA query findActiveSlabsForDistance returns exact slab for distance")
    void testFindActiveSlabsForDistance_AllRequiredDistances(int distanceMeters, String expectedCharge) {
        List<DeliveryPricingSlab> slabs = repository.findActiveSlabsForDistance(distanceMeters, LocalDateTime.now());

        assertThat(slabs).hasSize(1);
        assertThat(slabs.get(0).getDeliveryCharge()).isEqualByComparingTo(new BigDecimal(expectedCharge));
        assertThat(slabs.get(0).covers(distanceMeters)).isTrue();
    }

    @Test
    @DisplayName("7093m lookup correctly matches 7001-8000m slab")
    void test7093MetersLookup() {
        Optional<DeliveryPricingSlab> slabOpt = repository.findActiveSlabForDistance(7093);
        assertThat(slabOpt).isPresent();
        assertThat(slabOpt.get().getMinimumDistanceMeters()).isEqualTo(7001);
        assertThat(slabOpt.get().getMaximumDistanceMeters()).isEqualTo(8000);
        assertThat(slabOpt.get().getDeliveryCharge()).isEqualByComparingTo(new BigDecimal("45.00"));
    }

    @Test
    @DisplayName("7207m lookup correctly matches 7001-8000m slab")
    void test7207MetersLookup() {
        Optional<DeliveryPricingSlab> slabOpt = repository.findActiveSlabForDistance(7207);
        assertThat(slabOpt).isPresent();
        assertThat(slabOpt.get().getMinimumDistanceMeters()).isEqualTo(7001);
        assertThat(slabOpt.get().getMaximumDistanceMeters()).isEqualTo(8000);
        assertThat(slabOpt.get().getDeliveryCharge()).isEqualByComparingTo(new BigDecimal("45.00"));
    }

    @Test
    @DisplayName("Distance greater than 15,000m returns empty")
    void testOutOfRangeReturnsEmpty() {
        List<DeliveryPricingSlab> slabs = repository.findActiveSlabsForDistance(15001, LocalDateTime.now());
        assertThat(slabs).isEmpty();

        Optional<DeliveryPricingSlab> slabOpt = repository.findActiveSlabForDistance(15001);
        assertThat(slabOpt).isEmpty();
    }

    @Test
    @DisplayName("Inactive slabs are filtered out by repository query")
    void testInactiveSlabsIgnored() {
        // Deactivate slab 8 (7001 - 8000)
        DeliveryPricingSlab slab8 = repository.findActiveSlabForDistance(7093).orElseThrow();
        slab8.setActive(false);
        repository.save(slab8);

        List<DeliveryPricingSlab> slabs = repository.findActiveSlabsForDistance(7093, LocalDateTime.now());
        assertThat(slabs).isEmpty();
    }

    @Test
    @DisplayName("Future effective start date is excluded by query")
    void testFutureEffectiveStartDateExcluded() {
        DeliveryPricingSlab slab8 = repository.findActiveSlabForDistance(7093).orElseThrow();
        slab8.setEffectiveStartDate(LocalDateTime.now().plusDays(10));
        repository.save(slab8);

        List<DeliveryPricingSlab> slabs = repository.findActiveSlabsForDistance(7093, LocalDateTime.now());
        assertThat(slabs).isEmpty();
    }

    @Test
    @DisplayName("Past effective end date is excluded by query")
    void testPastEffectiveEndDateExcluded() {
        DeliveryPricingSlab slab8 = repository.findActiveSlabForDistance(7093).orElseThrow();
        slab8.setEffectiveEndDate(LocalDateTime.now().minusDays(1));
        repository.save(slab8);

        List<DeliveryPricingSlab> slabs = repository.findActiveSlabsForDistance(7093, LocalDateTime.now());
        assertThat(slabs).isEmpty();
    }

    @Test
    @DisplayName("Valid effective date range is included by query")
    void testValidEffectiveDateRangeIncluded() {
        DeliveryPricingSlab slab8 = repository.findActiveSlabForDistance(7093).orElseThrow();
        slab8.setEffectiveStartDate(LocalDateTime.now().minusDays(1));
        slab8.setEffectiveEndDate(LocalDateTime.now().plusDays(1));
        repository.save(slab8);

        List<DeliveryPricingSlab> slabs = repository.findActiveSlabsForDistance(7093, LocalDateTime.now());
        assertThat(slabs).hasSize(1);
        assertThat(slabs.get(0).getDeliveryCharge()).isEqualByComparingTo(new BigDecimal("45.00"));
    }
}
