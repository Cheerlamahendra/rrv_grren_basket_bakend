package com.rrvgreenbasket.inventory.repository;

import com.rrvgreenbasket.inventory.entity.StockAlert;
import com.rrvgreenbasket.inventory.enums.AlertStatus;
import com.rrvgreenbasket.inventory.enums.AlertType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockAlertRepository extends JpaRepository<StockAlert, Long>, JpaSpecificationExecutor<StockAlert> {

    Optional<StockAlert> findByPublicId(String publicId);

    Optional<StockAlert> findByProductPublicIdAndAlertTypeAndAlertStatusIn(
            String productPublicId,
            AlertType alertType,
            Collection<AlertStatus> alertStatuses
    );

    List<StockAlert> findByProductPublicIdAndAlertStatusIn(
            String productPublicId,
            Collection<AlertStatus> alertStatuses
    );

    Page<StockAlert> findByAlertStatus(AlertStatus alertStatus, Pageable pageable);

    Page<StockAlert> findByAlertStatusInOrderByCreatedAtDesc(
            Collection<AlertStatus> alertStatuses,
            Pageable pageable
    );
}
