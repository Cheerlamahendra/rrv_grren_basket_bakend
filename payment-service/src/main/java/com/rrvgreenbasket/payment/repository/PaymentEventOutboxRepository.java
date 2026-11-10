package com.rrvgreenbasket.payment.repository;

import com.rrvgreenbasket.payment.entity.PaymentEventOutbox;
import com.rrvgreenbasket.payment.enums.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentEventOutboxRepository extends JpaRepository<PaymentEventOutbox, Long> {

    Optional<PaymentEventOutbox> findByEventPublicId(String eventPublicId);

    List<PaymentEventOutbox> findByPublishingStatusAndRetryCountLessThanOrderByCreatedAtAsc(
            OutboxStatus publishingStatus, int maxRetries, Pageable pageable);
}
