package com.rrvgreenbasket.auth.repository;

import com.rrvgreenbasket.auth.entity.CustomerSession;
import com.rrvgreenbasket.auth.enums.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * CustomerSessionRepository
 *
 * <p><strong>What it does:</strong></p>
 * Data access operations for <code>customer_sessions</code> table.
 */
@Repository
public interface CustomerSessionRepository extends JpaRepository<CustomerSession, Long> {

    @Query("SELECT s FROM CustomerSession s JOIN FETCH s.authUser WHERE s.sessionId = :sessionId")
    Optional<CustomerSession> findBySessionId(@Param("sessionId") String sessionId);

    List<CustomerSession> findByAuthUserIdAndStatus(Long userId, SessionStatus status);

    long countByAuthUserIdAndStatus(Long userId, SessionStatus status);

    @Modifying
    @Query("UPDATE CustomerSession s SET s.status = :status, s.revokedAt = :revokedAt, s.revocationReason = :reason WHERE s.sessionId = :sessionId")
    int revokeSession(
            @Param("sessionId") String sessionId,
            @Param("status") SessionStatus status,
            @Param("revokedAt") LocalDateTime revokedAt,
            @Param("reason") String reason
    );

    @Modifying
    @Query("UPDATE CustomerSession s SET s.lastActiveAt = :lastActiveAt WHERE s.sessionId = :sessionId")
    int touchSession(
            @Param("sessionId") String sessionId,
            @Param("lastActiveAt") LocalDateTime lastActiveAt
    );
}
