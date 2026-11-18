package com.rrvgreenbasket.delivery.repository;

import com.rrvgreenbasket.delivery.entity.DeliveryPricingSlab;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryPricingSlabRepository extends JpaRepository<DeliveryPricingSlab, Long> {

    List<DeliveryPricingSlab> findByActiveTrueOrderByMinimumDistanceMetersAsc();

    List<DeliveryPricingSlab> findAllByOrderByMinimumDistanceMetersAsc();

    @Query("SELECT s FROM DeliveryPricingSlab s WHERE s.active = true " +
           "AND :distanceMeters >= s.minimumDistanceMeters " +
           "AND :distanceMeters <= s.maximumDistanceMeters")
    Optional<DeliveryPricingSlab> findActiveSlabForDistance(@Param("distanceMeters") int distanceMeters);
}
