package com.rrvgreenbasket.inventory.repository;

import com.rrvgreenbasket.inventory.entity.StockReservationRequest;
import com.rrvgreenbasket.inventory.enums.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockReservationRequestRepository extends JpaRepository<StockReservationRequest, Long> {

    Optional<StockReservationRequest> findByPublicId(String publicId);

    Optional<StockReservationRequest> findByIdempotencyKey(String idempotencyKey);

    List<StockReservationRequest> findByOrderPublicId(String orderPublicId);

    Optional<StockReservationRequest> findFirstByOrderPublicIdOrderByCreatedAtDesc(String orderPublicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM StockReservationRequest r WHERE r.publicId = :publicId")
    Optional<StockReservationRequest> findByPublicIdWithLock(@Param("publicId") String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM StockReservationRequest r WHERE r.idempotencyKey = :idempotencyKey")
    Optional<StockReservationRequest> findByIdempotencyKeyWithLock(@Param("idempotencyKey") String idempotencyKey);

    @Query("SELECT DISTINCT r FROM StockReservationRequest r LEFT JOIN FETCH r.items WHERE r.status = :status AND r.expiresAt < :cutoffTime")
    List<StockReservationRequest> findByStatusAndExpiresAtBefore(@Param("status") ReservationStatus status, @Param("cutoffTime") Instant cutoffTime);
}
