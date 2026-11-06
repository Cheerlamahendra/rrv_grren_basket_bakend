package com.rrvgreenbasket.auth.repository;

import com.rrvgreenbasket.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * RefreshTokenRepository
 *
 * <p><strong>What it does:</strong></p>
 * Manages persistence, lookup, and family-level revocation in <code>refresh_tokens</code>.
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    @Query("SELECT r FROM RefreshToken r JOIN FETCH r.authUser WHERE r.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revokedAt = :now WHERE r.tokenFamilyId = :familyId AND r.revokedAt IS NULL")
    int revokeFamily(@Param("familyId") String familyId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revokedAt = :now WHERE r.authUser.id = :userId AND r.revokedAt IS NULL")
    int revokeAllByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
