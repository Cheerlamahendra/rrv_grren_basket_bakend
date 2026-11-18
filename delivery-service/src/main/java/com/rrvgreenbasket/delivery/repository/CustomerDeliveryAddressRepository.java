package com.rrvgreenbasket.delivery.repository;

import com.rrvgreenbasket.delivery.entity.CustomerDeliveryAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerDeliveryAddressRepository extends JpaRepository<CustomerDeliveryAddress, Long> {

    List<CustomerDeliveryAddress> findByCustomerIdOrderByIsDefaultDescCreatedAtDesc(String customerId);

    Optional<CustomerDeliveryAddress> findByCustomerIdAndPublicId(String customerId, String publicId);

    Optional<CustomerDeliveryAddress> findByCustomerIdAndIsDefaultTrue(String customerId);
}
