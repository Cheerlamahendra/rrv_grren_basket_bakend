package com.rrvgreenbasket.delivery.repository;

import com.rrvgreenbasket.delivery.entity.DeliveryPricingSlab;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryPricingSlabRepository extends JpaRepository<DeliveryPricingSlab, Long> {

    List<DeliveryPricingSlab> findByActiveTrueOrderByMinimumDistanceMetersAsc();

    List<DeliveryPricingSlab> findAllByOrderByMinimumDistanceMetersAsc();

    @Query("SELECT s FROM DeliveryPricingSlab s WHERE s.active = true " +
           "AND s.minimumDistanceMeters <= :distanceMeters " +
           "AND s.maximumDistanceMeters >= :distanceMeters " +
           "AND (s.effectiveStartDate IS NULL OR s.effectiveStartDate <= :now) " +
           "AND (s.effectiveEndDate IS NULL OR s.effectiveEndDate >= :now) " +
           "ORDER BY s.minimumDistanceMeters ASC")
    List<DeliveryPricingSlab> findActiveSlabsForDistance(
            @Param("distanceMeters") int distanceMeters,
            @Param("now") LocalDateTime now);

    default List<DeliveryPricingSlab> findActiveSlabsForDistance(int distanceMeters) {
        return findActiveSlabsForDistance(distanceMeters, LocalDateTime.now());
    }

    @Query("SELECT s FROM DeliveryPricingSlab s WHERE s.active = true " +
           "AND s.minimumDistanceMeters <= :distanceMeters " +
           "AND s.maximumDistanceMeters >= :distanceMeters " +
           "AND (s.effectiveStartDate IS NULL OR s.effectiveStartDate <= CURRENT_TIMESTAMP) " +
           "AND (s.effectiveEndDate IS NULL OR s.effectiveEndDate >= CURRENT_TIMESTAMP) " +
           "ORDER BY s.minimumDistanceMeters ASC")
    Optional<DeliveryPricingSlab> findActiveSlabForDistance(@Param("distanceMeters") int distanceMeters);

    default Optional<DeliveryPricingSlab> findActiveSlabForDistance(int distanceMeters, LocalDateTime now) {
        List<DeliveryPricingSlab> slabs = findActiveSlabsForDistance(distanceMeters, now);
        return slabs.isEmpty() ? Optional.empty() : Optional.of(slabs.get(0));
    }
}
