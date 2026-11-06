package com.rrvgreenbasket.customer.repository;

import com.rrvgreenbasket.customer.entity.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * CustomerAddressRepository
 *
 * <p><strong>What it does:</strong></p>
 * Data access abstraction for {@link CustomerAddress} entities.
 * Enforces customer scoping, bulk default address resets, and ordered retrieval.
 */
@Repository
public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, Long> {

    List<CustomerAddress> findByCustomerIdOrderByIsDefaultDescCreatedAtDesc(Long customerId);

    List<CustomerAddress> findByCustomerPublicIdOrderByIsDefaultDescCreatedAtDesc(String customerPublicId);

    Optional<CustomerAddress> findByPublicIdAndCustomerId(String publicId, Long customerId);

    Optional<CustomerAddress> findByPublicIdAndCustomerPublicId(String publicId, String customerPublicId);

    Optional<CustomerAddress> findByPublicId(String publicId);

    long countByCustomerId(Long customerId);

    Optional<CustomerAddress> findByCustomerIdAndIsDefaultTrue(Long customerId);

    @Modifying
    @Query("UPDATE CustomerAddress a SET a.isDefault = false WHERE a.customer.id = :customerId")
    void resetDefaultAddressesForCustomer(@Param("customerId") Long customerId);

    Optional<CustomerAddress> findFirstByCustomerIdOrderByUpdatedAtDesc(Long customerId);
}
