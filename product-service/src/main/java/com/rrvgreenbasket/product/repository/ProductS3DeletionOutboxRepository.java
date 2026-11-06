package com.rrvgreenbasket.product.repository;

import com.rrvgreenbasket.product.entity.ProductS3DeletionOutbox;
import com.rrvgreenbasket.product.enums.S3DeletionStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductS3DeletionOutboxRepository extends JpaRepository<ProductS3DeletionOutbox, Long> {

    Optional<ProductS3DeletionOutbox> findByPublicId(String publicId);

    List<ProductS3DeletionOutbox> findByProductPublicId(String productPublicId);

    @Query("SELECT o FROM ProductS3DeletionOutbox o WHERE o.status = :status AND o.nextRetryAt <= :now ORDER BY o.createdAt ASC")
    List<ProductS3DeletionOutbox> findPendingDeletions(
            @Param("status") S3DeletionStatus status,
            @Param("now") Instant now,
            Pageable pageable);
}
