package com.rrvgreenbasket.payment.repository;

import com.rrvgreenbasket.payment.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByTransactionPublicId(String transactionPublicId);

    List<PaymentTransaction> findByPaymentIdOrderByCreatedAtDesc(Long paymentId);

    Optional<PaymentTransaction> findByProviderPaymentId(String providerPaymentId);

    Optional<PaymentTransaction> findByProviderOrderId(String providerOrderId);

    List<PaymentTransaction> findByPaymentPaymentPublicIdOrderByCreatedAtDesc(String paymentPublicId);
}
