package com.rrvgreenbasket.cart.repository;

import com.rrvgreenbasket.cart.entity.Cart;
import com.rrvgreenbasket.cart.entity.CartStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByCustomerPublicIdAndStatus(
            String customerPublicId,
            CartStatus status
    );

    Optional<Cart> findByPublicId(String publicId);

    boolean existsByCustomerPublicIdAndStatus(
            String customerPublicId,
            CartStatus status
    );
}