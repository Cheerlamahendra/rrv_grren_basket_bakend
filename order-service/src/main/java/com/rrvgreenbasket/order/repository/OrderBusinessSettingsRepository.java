package com.rrvgreenbasket.order.repository;

import com.rrvgreenbasket.order.entity.OrderBusinessSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderBusinessSettingsRepository extends JpaRepository<OrderBusinessSettings, Long> {
    Optional<OrderBusinessSettings> findFirstByOrderByIdAsc();
}
