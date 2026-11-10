package com.rrvgreenbasket.payment.repository;

import com.rrvgreenbasket.payment.entity.Payment;
import com.rrvgreenbasket.payment.enums.PaymentMethod;
import com.rrvgreenbasket.payment.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByPaymentPublicId(String paymentPublicId);

    Optional<Payment> findByOrderPublicId(String orderPublicId);

    List<Payment> findAllByOrderPublicId(String orderPublicId);

    Page<Payment> findByCustomerPublicId(String customerPublicId, Pageable pageable);

    Page<Payment> findByPaymentStatus(PaymentStatus status, Pageable pageable);

    Page<Payment> findByPaymentMethod(PaymentMethod method, Pageable pageable);

    List<Payment> findByPaymentStatusInAndCreatedAtBefore(List<PaymentStatus> statuses, Instant thresholdTime);

    @Query("SELECT COUNT(p) FROM Payment p")
    long countTotalPayments();

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.paymentMethod = :method")
    long countByPaymentMethod(PaymentMethod method);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.paymentStatus = :status")
    long countByPaymentStatus(PaymentStatus status);

    @Query("SELECT COALESCE(SUM(p.totalAmount), 0) FROM Payment p")
    BigDecimal sumTotalOrderAmount();

    @Query("SELECT COALESCE(SUM(p.advanceAmount), 0) FROM Payment p WHERE p.paymentMethod = 'PARTIAL_ONLINE_COD'")
    BigDecimal sumTotalAdvanceAmountPayable();

    @Query("SELECT COALESCE(SUM(p.advancePaidAmount), 0) FROM Payment p")
    BigDecimal sumTotalAdvancePaidAmount();

    @Query("SELECT COALESCE(SUM(p.deliveryCollectionAmount), 0) FROM Payment p WHERE p.paymentMethod = 'PARTIAL_ONLINE_COD'")
    BigDecimal sumTotalDeliveryCashPayable();

    @Query("SELECT COALESCE(SUM(p.deliveryCollectedAmount), 0) FROM Payment p")
    BigDecimal sumTotalDeliveryCashCollected();

    @Query("SELECT COALESCE(SUM(p.outstandingAmount), 0) FROM Payment p")
    BigDecimal sumTotalOutstandingAmount();

    @Query("SELECT COALESCE(SUM(p.refundedAmount), 0) FROM Payment p")
    BigDecimal sumTotalRefundedAmount();
}
