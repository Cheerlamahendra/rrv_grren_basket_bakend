package com.rrvgreenbasket.inventory.repository;

import com.rrvgreenbasket.inventory.entity.StockMovement;
import com.rrvgreenbasket.inventory.enums.MovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long>, JpaSpecificationExecutor<StockMovement> {

    Optional<StockMovement> findByPublicId(String publicId);

    Page<StockMovement> findByProductPublicIdOrderByCreatedAtDesc(String productPublicId, Pageable pageable);

    Page<StockMovement> findByMovementTypeOrderByCreatedAtDesc(MovementType movementType, Pageable pageable);

    List<StockMovement> findByReferenceTypeAndReferenceId(String referenceType, String referenceId);

    long countByProductPublicId(String productPublicId);
}
