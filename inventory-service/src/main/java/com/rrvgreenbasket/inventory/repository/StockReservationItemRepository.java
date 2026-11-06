package com.rrvgreenbasket.inventory.repository;

import com.rrvgreenbasket.inventory.entity.StockReservationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockReservationItemRepository extends JpaRepository<StockReservationItem, Long> {

    Optional<StockReservationItem> findByPublicId(String publicId);

    List<StockReservationItem> findByReservationRequestId(Long reservationRequestId);

    List<StockReservationItem> findByOrderPublicId(String orderPublicId);

    List<StockReservationItem> findByProductPublicId(String productPublicId);
}
