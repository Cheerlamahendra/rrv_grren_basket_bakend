package com.rrvgreenbasket.auth.repository;

import com.rrvgreenbasket.auth.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * PasswordResetTokenRepository
 *
 * <p><strong>What it does:</strong></p>
 * Data access operations for <code>password_reset_tokens</code> records.
 * Supports single-use, short-lived reset authorization tokens.
 */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.usedAt = :now WHERE t.authUser.id = :userId AND t.usedAt IS NULL")
    void revokeAllActiveByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
