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
 * Data access operations for <code>customer_auth</code> records by mobile number.
 */
@Repository
public interface CustomerAuthRepository extends JpaRepository<CustomerAuth, Long> {

    @Query("SELECT c FROM CustomerAuth c JOIN FETCH c.user WHERE c.mobileNumber = :mobileNumber")
    Optional<CustomerAuth> findByMobileNumber(@Param("mobileNumber") String mobileNumber);

    boolean existsByMobileNumber(String mobileNumber);
}
