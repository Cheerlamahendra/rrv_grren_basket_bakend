package com.rrvgreenbasket.inventory.repository;

import com.rrvgreenbasket.inventory.entity.InventoryItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long>, JpaSpecificationExecutor<InventoryItem> {

    Optional<InventoryItem> findByPublicId(String publicId);

    Optional<InventoryItem> findByProductPublicId(String productPublicId);

    boolean existsByProductPublicId(String productPublicId);

    List<InventoryItem> findByProductPublicIdIn(Collection<String> productPublicIds);

    Page<InventoryItem> findByInitializedTrue(Pageable pageable);

    Page<InventoryItem> findByAvailableQuantityLessThanEqual(BigDecimal threshold, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM InventoryItem i WHERE i.productPublicId = :productPublicId")
    Optional<InventoryItem> findByProductPublicIdWithLock(@Param("productPublicId") String productPublicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM InventoryItem i WHERE i.productPublicId IN :productPublicIds ORDER BY i.productPublicId ASC")
    List<InventoryItem> findByProductPublicIdInWithLock(@Param("productPublicIds") List<String> productPublicIds);
}
