package com.rrvgreenbasket.delivery.repository;

import com.rrvgreenbasket.delivery.entity.StoreLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StoreLocationRepository extends JpaRepository<StoreLocation, Long> {

    Optional<StoreLocation> findByPrimaryStoreTrueAndDeliveryEnabledTrue();

    Optional<StoreLocation> findByPrimaryStoreTrue();

    Optional<StoreLocation> findByPublicId(String publicId);

    boolean existsByPublicId(String publicId);
}
