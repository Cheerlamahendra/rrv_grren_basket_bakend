package com.rrvgreenbasket.customer.repository;

import com.rrvgreenbasket.customer.entity.Customer;
import com.rrvgreenbasket.customer.enums.AccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * CustomerRepository
 *
 * <p><strong>What it does:</strong></p>
 * Data access abstraction for {@link Customer} entities.
 * Supports lookups by external public UUID, linked Auth user UUID,
 * unique mobile number, email, and administrative search.
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByPublicId(String publicId);

    Optional<Customer> findByAuthUserId(String authUserId);

    Optional<Customer> findByMobileNumber(String mobileNumber);

    Optional<Customer> findByEmail(String email);

    boolean existsByAuthUserId(String authUserId);

    boolean existsByMobileNumber(String mobileNumber);

    boolean existsByEmail(String email);

    Page<Customer> findByStatus(AccountStatus status, Pageable pageable);

    @Query("SELECT c FROM Customer c WHERE " +
           "(:query IS NULL OR :query = '' OR " +
           "LOWER(c.fullName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.email) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "c.mobileNumber LIKE CONCAT('%', :query, '%')) AND " +
           "(:status IS NULL OR c.status = :status)")
    Page<Customer> searchCustomers(@Param("query") String query,
                                  @Param("status") AccountStatus status,
                                  Pageable pageable);
}
