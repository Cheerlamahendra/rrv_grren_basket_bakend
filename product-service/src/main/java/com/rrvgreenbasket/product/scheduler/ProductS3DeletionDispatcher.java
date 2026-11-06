package com.rrvgreenbasket.product.scheduler;

import com.rrvgreenbasket.product.entity.ProductS3DeletionOutbox;
import com.rrvgreenbasket.product.enums.S3DeletionStatus;
import com.rrvgreenbasket.product.repository.ProductS3DeletionOutboxRepository;
import com.rrvgreenbasket.product.service.ImageStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductS3DeletionDispatcher {

    private final ProductS3DeletionOutboxRepository outboxRepository;
    private final ImageStorageService imageStorageService;

    @Value("${app.s3.deletion-outbox.enabled:true}")
    private boolean outboxEnabled;

    @Value("${app.s3.deletion-outbox.batch-size:50}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${app.s3.deletion-outbox.poll-interval-ms:5000}")
    public void processPendingDeletions() {
        if (!outboxEnabled) {
            return;
        }

        Instant now = Instant.now();
        List<ProductS3DeletionOutbox> pendingList = outboxRepository.findPendingDeletions(
                S3DeletionStatus.PENDING, now, PageRequest.of(0, batchSize));

        if (pendingList.isEmpty()) {
            return;
        }

        log.info("Found {} pending S3 deletion outbox items to process", pendingList.size());
        for (ProductS3DeletionOutbox entry : pendingList) {
            processSingleOutboxEntry(entry);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processSingleOutboxEntry(ProductS3DeletionOutbox entry) {
        try {
            log.info("Deleting image from S3 for outbox entry {}: key={}", entry.getPublicId(), entry.getStorageKey());
            imageStorageService.deleteImage(entry.getStorageKey());
            entry.setStatus(S3DeletionStatus.COMPLETED);
            entry.setLastError(null);
            entry.setUpdatedAt(Instant.now());
            outboxRepository.save(entry);
            log.info("Successfully deleted S3 object and marked outbox entry {} as COMPLETED", entry.getPublicId());
        } catch (Exception ex) {
            log.error("Failed to delete S3 object for outbox entry {}: key={}", entry.getPublicId(), entry.getStorageKey(), ex);
            int newRetryCount = entry.getRetryCount() + 1;
            entry.setRetryCount(newRetryCount);
            entry.setLastError(ex.getMessage() != null && ex.getMessage().length() > 500
                    ? ex.getMessage().substring(0, 497) + "..."
                    : ex.getMessage());
            entry.setUpdatedAt(Instant.now());

            if (newRetryCount >= entry.getMaxRetries()) {
                entry.setStatus(S3DeletionStatus.FAILED);
                log.error("Outbox entry {} reached max retries ({}), marking as FAILED", entry.getPublicId(), entry.getMaxRetries());
            } else {
                long backoffSeconds = (long) Math.pow(2, newRetryCount) * 30; // 60s, 120s, 240s...
                entry.setNextRetryAt(Instant.now().plusSeconds(backoffSeconds));
                log.warn("Outbox entry {} will retry in {} seconds (retry #{})", entry.getPublicId(), backoffSeconds, newRetryCount);
            }
            outboxRepository.save(entry);
        }
    }
}
