package com.rrvgreenbasket.payment.repository;

import com.rrvgreenbasket.payment.entity.PaymentRefund;
import com.rrvgreenbasket.payment.enums.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, Long> {

    Optional<PaymentRefund> findByRefundPublicId(String refundPublicId);

    Optional<PaymentRefund> findByIdempotencyKey(String idempotencyKey);

    Optional<PaymentRefund> findByProviderRefundId(String providerRefundId);

    List<PaymentRefund> findByPaymentIdOrderByCreatedAtDesc(Long paymentId);

    List<PaymentRefund> findByPaymentPaymentPublicIdOrderByCreatedAtDesc(String paymentPublicId);

    List<PaymentRefund> findByPaymentIdAndRefundStatus(Long paymentId, RefundStatus status);
}
