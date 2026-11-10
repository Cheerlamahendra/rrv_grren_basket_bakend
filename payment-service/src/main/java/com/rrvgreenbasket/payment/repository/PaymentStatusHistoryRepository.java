package com.rrvgreenbasket.payment.repository;

import com.rrvgreenbasket.payment.entity.PaymentStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentStatusHistoryRepository extends JpaRepository<PaymentStatusHistory, Long> {

    List<PaymentStatusHistory> findByPaymentIdOrderByChangedAtDesc(Long paymentId);

    List<PaymentStatusHistory> findByPaymentPaymentPublicIdOrderByChangedAtDesc(String paymentPublicId);
}
