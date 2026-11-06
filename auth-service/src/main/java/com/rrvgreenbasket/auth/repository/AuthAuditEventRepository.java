package com.rrvgreenbasket.auth.repository;

import com.rrvgreenbasket.auth.entity.AuthAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * AuthAuditEventRepository
 *
 * <p><strong>What it does:</strong></p>
 * Persistence and retrieval for security audit records in <code>auth_audit_events</code>.
 */
@Repository
public interface AuthAuditEventRepository extends JpaRepository<AuthAuditEvent, Long> {

    List<AuthAuditEvent> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<AuthAuditEvent> findByEventTypeOrderByCreatedAtDesc(String eventType);
}
