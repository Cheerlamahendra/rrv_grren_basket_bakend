package com.rrvgreenbasket.store.repository;

import com.rrvgreenbasket.store.entity.StoreSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StoreSettingsRepository extends JpaRepository<StoreSettings, Long> {

    Optional<StoreSettings> findFirstByIsActiveTrueOrderByUpdatedAtDesc();

    Optional<StoreSettings> findByPublicId(String publicId);
}
