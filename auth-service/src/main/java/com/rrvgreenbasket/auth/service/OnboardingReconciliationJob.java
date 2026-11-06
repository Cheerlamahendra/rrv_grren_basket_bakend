package com.rrvgreenbasket.auth.service;

import com.rrvgreenbasket.auth.client.CustomerServiceClient;
import com.rrvgreenbasket.auth.entity.AuthUser;
import com.rrvgreenbasket.auth.enums.UserStatus;
import com.rrvgreenbasket.auth.repository.AuthUserRepository;
import com.rrvgreenbasket.auth.repository.CustomerAuthRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * OnboardingReconciliationJob
 *
 * <p><strong>What it does:</strong></p>
 * Background reconciliation job that detects dangling PENDING onboarding records
 * resulting from intermittent network timeouts during registration.
 * Distinguishes NOT_FOUND (purges staging) from SERVICE_UNAVAILABLE (preserves for next cycle).
 */
@Component
public class OnboardingReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(OnboardingReconciliationJob.class);

    private final AuthUserRepository authUserRepository;
    private final CustomerAuthRepository customerAuthRepository;
    private final CustomerServiceClient customerServiceClient;

    public OnboardingReconciliationJob(
            AuthUserRepository authUserRepository,
            CustomerAuthRepository customerAuthRepository,
            CustomerServiceClient customerServiceClient) {
        this.authUserRepository = authUserRepository;
        this.customerAuthRepository = customerAuthRepository;
        this.customerServiceClient = customerServiceClient;
    }

    @Scheduled(fixedRate = 600000, initialDelay = 60000) // Every 10 minutes
    @Transactional
    public void reconcilePendingOnboardings() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(10);
        List<AuthUser> pendingUsers = authUserRepository.findAll().stream()
                .filter(u -> u.getStatus() == UserStatus.PENDING && u.getCreatedAt().isBefore(threshold))
                .toList();

        if (pendingUsers.isEmpty()) {
            return;
        }

        log.info("Running onboarding reconciliation for {} pending account(s)", pendingUsers.size());

        for (AuthUser user : pendingUsers) {
            try {
                boolean exists = customerServiceClient.verifyProfileExists(user.getPublicId());
                if (exists) {
                    log.info("Reconciliation: Profile found for authUserId='{}'. Promoting to ACTIVE.", user.getPublicId());
                    user.setStatus(UserStatus.ACTIVE);
                    authUserRepository.save(user);
                } else {
                    log.info("Reconciliation: Profile confirmed NOT_FOUND for authUserId='{}'. Purging staging.", user.getPublicId());
                    if (user.getCustomerAuth() != null) {
                        customerAuthRepository.delete(user.getCustomerAuth());
                    }
                    authUserRepository.delete(user);
                }
            } catch (Exception ex) {
                log.warn("Reconciliation: Could not probe Customer Service for authUserId='{}' (preserving status): {}",
                        user.getPublicId(), ex.getMessage());
            }
        }
    }
}
