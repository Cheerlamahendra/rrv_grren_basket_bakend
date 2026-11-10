package com.rrvgreenbasket.review.repository;

import com.rrvgreenbasket.review.entity.ReviewAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewAuditEventRepository extends JpaRepository<ReviewAuditEvent, Long> {

    List<ReviewAuditEvent> findByReviewPublicIdOrderByCreatedAtDesc(String reviewPublicId);
}
