package com.rrvgreenbasket.auth.repository;

import com.rrvgreenbasket.auth.entity.AdminAuth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * AdminAuthRepository
 *
 * <p><strong>What it does:</strong></p>
 * Data access operations for <code>admin_auth</code> records by admin email.
 */
@Repository
public interface AdminAuthRepository extends JpaRepository<AdminAuth, Long> {

    @Query("SELECT a FROM AdminAuth a JOIN FETCH a.authUser WHERE a.email = :email")
    Optional<AdminAuth> findByEmail(@Param("email") String email);

    boolean existsByEmail(String email);
}
