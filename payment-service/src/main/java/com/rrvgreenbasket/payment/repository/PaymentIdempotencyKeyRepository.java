package com.rrvgreenbasket.payment.repository;

import com.rrvgreenbasket.payment.entity.PaymentIdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface PaymentIdempotencyKeyRepository extends JpaRepository<PaymentIdempotencyKey, Long> {

    Optional<PaymentIdempotencyKey> findByIdempotencyKey(String idempotencyKey);

    Optional<PaymentIdempotencyKey> findByOperationTypeAndResourceReference(String operationType, String resourceReference);

    void deleteByExpiresAtBefore(Instant now);
}
