package com.rrvgreenbasket.auth.repository;

import com.rrvgreenbasket.auth.entity.AuthUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * AuthUserRepository
 *
 * <p><strong>What it does:</strong></p>
 * Data access operations for central <code>auth_users</code> records.
 */
@Repository
public interface AuthUserRepository extends JpaRepository<AuthUser, Long> {

    @Query("SELECT u FROM AuthUser u LEFT JOIN FETCH u.adminAuth LEFT JOIN FETCH u.customerAuth WHERE u.publicId = :publicId")
    Optional<AuthUser> findByPublicId(@Param("publicId") String publicId);
}
