package com.rrvgreenbasket.inventory.repository;

import com.rrvgreenbasket.inventory.entity.InventoryEventOutbox;
import com.rrvgreenbasket.inventory.enums.OutboxEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryEventOutboxRepository extends JpaRepository<InventoryEventOutbox, Long> {

    Optional<InventoryEventOutbox> findByEventId(String eventId);

    List<InventoryEventOutbox> findTop50ByEventStatusOrderByCreatedAtAsc(OutboxEventStatus eventStatus);

    List<InventoryEventOutbox> findTop50ByEventStatusAndRetryCountLessThanOrderByCreatedAtAsc(
            OutboxEventStatus eventStatus,
            Integer maxRetries
    );

    List<InventoryEventOutbox> findByEventStatusAndCreatedAtBefore(
            OutboxEventStatus eventStatus,
            java.time.Instant cutoff
    );

    long countByEventStatus(OutboxEventStatus status);
}
