package com.rrvgreenbasket.payment.repository;

import com.rrvgreenbasket.payment.entity.CodCollection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CodCollectionRepository extends JpaRepository<CodCollection, Long> {

    Optional<CodCollection> findByCollectionPublicId(String collectionPublicId);

    Optional<CodCollection> findByIdempotencyKey(String idempotencyKey);

    List<CodCollection> findByPaymentId(Long paymentId);

    List<CodCollection> findByOrderPublicId(String orderPublicId);

    Page<CodCollection> findAllByOrderByCollectedAtDesc(Pageable pageable);

    List<CodCollection> findByPaymentPaymentPublicIdOrderByCollectedAtDesc(String paymentPublicId);
}
