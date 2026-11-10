package com.rrvgreenbasket.payment.scheduler;

import com.rrvgreenbasket.payment.service.PaymentReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconciliationScheduler {

    private final PaymentReconciliationService reconciliationService;

    @Scheduled(fixedDelayString = "${payment.reconciliation.poll-interval-ms:60000}")
    public void runReconciliation() {
        try {
            reconciliationService.reconcilePendingPayments();
        } catch (Exception ex) {
            log.error("Scheduled payment reconciliation execution error: {}", ex.getMessage(), ex);
        }
    }
}
