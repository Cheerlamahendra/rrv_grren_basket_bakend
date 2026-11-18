package com.rrvgreenbasket.delivery.repository;

import com.rrvgreenbasket.delivery.entity.DeliveryZone;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryZoneRepository extends JpaRepository<DeliveryZone, Long>, JpaSpecificationExecutor<DeliveryZone> {

    Optional<DeliveryZone> findByPublicId(String publicId);

    Optional<DeliveryZone> findByPublicIdAndDeletedFalse(String publicId);

    Optional<DeliveryZone> findByZoneCode(String zoneCode);

    Optional<DeliveryZone> findByZoneCodeAndDeletedFalse(String zoneCode);

    boolean existsByZoneCode(String zoneCode);

    boolean existsByZoneCodeAndDeletedFalse(String zoneCode);

    boolean existsByZoneCodeAndPublicIdNot(String zoneCode, String publicId);

    boolean existsByZoneCodeAndPublicIdNotAndDeletedFalse(String zoneCode, String publicId);

    List<DeliveryZone> findByActiveTrue();

    List<DeliveryZone> findByActiveTrueAndDeletedFalse();

    List<DeliveryZone> findByCityIgnoreCaseAndStateIgnoreCaseAndActiveTrueAndDeletedFalse(String city, String state);

    Page<DeliveryZone> findAll(Specification<DeliveryZone> spec, Pageable pageable);
}
