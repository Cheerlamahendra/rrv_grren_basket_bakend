package com.rrvgreenbasket.cart.repository;

import com.rrvgreenbasket.cart.entity.Cart;
import com.rrvgreenbasket.cart.entity.CartStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    @EntityGraph(attributePaths = {"items"})
    Optional<Cart> findByCustomerPublicIdAndStatus(
            String customerPublicId,
            CartStatus status
    );

    @EntityGraph(attributePaths = {"items"})
    Optional<Cart> findByPublicId(String publicId);

    @EntityGraph(attributePaths = {"items"})
    Optional<Cart> findByPublicIdAndCustomerPublicId(String publicId, String customerPublicId);

    boolean existsByCustomerPublicIdAndStatus(
            String customerPublicId,
            CartStatus status
    );

    Page<Cart> findByStatusAndExpiresAtBefore(
            CartStatus status,
            LocalDateTime expirationThreshold,
            Pageable pageable
    );

    @Modifying
    @Query("UPDATE Cart c SET c.status = :newStatus WHERE c.id IN :cartIds")
    int updateStatusForCartIds(@Param("cartIds") List<Long> cartIds, @Param("newStatus") CartStatus newStatus);
}
