package com.rrvgreenbasket.order.repository;

import com.rrvgreenbasket.order.entity.Order;
import com.rrvgreenbasket.order.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    Optional<Order> findByPublicId(String publicId);

    Optional<Order> findByOrderNumber(String orderNumber);

    Optional<Order> findByCustomerPublicIdAndIdempotencyKey(String customerPublicId, String idempotencyKey);

    Page<Order> findByCustomerPublicId(String customerPublicId, Pageable pageable);

    Page<Order> findAll(Specification<Order> spec, Pageable pageable);

    long countByOrderStatus(OrderStatus status);

    long countByOrderPlacedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.orderStatus = :status AND o.orderPlacedAt BETWEEN :start AND :end")
    long countByStatusAndDateRange(@Param("status") OrderStatus status, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.orderStatus NOT IN ('CANCELLED', 'FAILED')")
    BigDecimal sumTotalRevenue();

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.orderStatus NOT IN ('CANCELLED', 'FAILED') AND o.orderPlacedAt BETWEEN :start AND :end")
    BigDecimal sumRevenueBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
