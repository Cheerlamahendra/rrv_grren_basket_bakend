package com.rrvgreenbasket.auth.repository;

import com.rrvgreenbasket.auth.entity.CustomerAuth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * CustomerAuthRepository
 *
 * <p><strong>What it does:</strong></p>
 * Data access operations for <code>customer_auth</code> records.
 * Supports lookups by mobile number (legacy) and email (V3 email/password auth).
 */
@Repository
public interface CustomerAuthRepository extends JpaRepository<CustomerAuth, Long> {

    @Query("SELECT c FROM CustomerAuth c JOIN FETCH c.user WHERE c.mobileNumber = :mobileNumber")
    Optional<CustomerAuth> findByMobileNumber(@Param("mobileNumber") String mobileNumber);

    boolean existsByMobileNumber(String mobileNumber);

    @Query("SELECT c FROM CustomerAuth c JOIN FETCH c.user WHERE c.email = :email")
    Optional<CustomerAuth> findByEmail(@Param("email") String email);

    boolean existsByEmail(String email);
}
