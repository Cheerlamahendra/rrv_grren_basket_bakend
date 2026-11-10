package com.rrvgreenbasket.payment.repository;

import com.rrvgreenbasket.payment.entity.PaymentWebhookEvent;
import com.rrvgreenbasket.payment.enums.PaymentProvider;
import com.rrvgreenbasket.payment.enums.WebhookProcessingStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, Long> {

    Optional<PaymentWebhookEvent> findByProviderAndProviderEventId(PaymentProvider provider, String providerEventId);

    boolean existsByProviderAndProviderEventId(PaymentProvider provider, String providerEventId);

    List<PaymentWebhookEvent> findByProcessingStatus(WebhookProcessingStatus processingStatus, Pageable pageable);
}
