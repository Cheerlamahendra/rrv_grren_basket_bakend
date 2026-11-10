package com.rrvgreenbasket.store.repository;

import com.rrvgreenbasket.store.entity.StoreSettingsAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StoreSettingsAuditRepository extends JpaRepository<StoreSettingsAudit, Long> {

    List<StoreSettingsAudit> findByStoreSettingsPublicIdOrderByCreatedAtDesc(String storeSettingsPublicId);
}
