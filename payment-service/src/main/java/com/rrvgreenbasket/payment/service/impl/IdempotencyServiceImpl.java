package com.rrvgreenbasket.payment.service.impl;

import com.rrvgreenbasket.payment.entity.PaymentIdempotencyKey;
import com.rrvgreenbasket.payment.enums.IdempotencyStatus;
import com.rrvgreenbasket.payment.exception.IdempotencyConflictException;
import com.rrvgreenbasket.payment.repository.PaymentIdempotencyKeyRepository;
import com.rrvgreenbasket.payment.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyServiceImpl implements IdempotencyService {

    private final PaymentIdempotencyKeyRepository idempotencyRepository;

    @Override
    @Transactional
    public IdempotencyCheck checkAndAcquire(String idempotencyKey, String operationType, String resourceRef, String requestPayload) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key cannot be null or empty");
        }

        String requestHash = hashPayload(requestPayload != null ? requestPayload : "");

        Optional<PaymentIdempotencyKey> existingOpt = idempotencyRepository.findByIdempotencyKey(idempotencyKey);
        if (existingOpt.isPresent()) {
            PaymentIdempotencyKey existing = existingOpt.get();

            if (!existing.getRequestHash().equals(requestHash)) {
                log.warn("Idempotency conflict: key '{}' reused with different request payload", idempotencyKey);
                throw new IdempotencyConflictException("Idempotency key has already been used with different request parameters.");
            }

            if (existing.getStatus() == IdempotencyStatus.COMPLETED) {
                log.info("Idempotent request recognized: key '{}' already completed. Returning cached reference.", idempotencyKey);
                return new IdempotencyCheck(IdempotencyResult.ALREADY_COMPLETED, existing.getResponseReference());
            }

            if (existing.getStatus() == IdempotencyStatus.PENDING) {
                if (existing.isExpired()) {
                    log.info("Expired pending idempotency key '{}'. Re-acquiring.", idempotencyKey);
                    existing.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
                    idempotencyRepository.save(existing);
                    return new IdempotencyCheck(IdempotencyResult.NEW, null);
                }
                log.warn("Concurrent operation in progress for idempotency key '{}'", idempotencyKey);
                throw new IdempotencyConflictException("An identical operation is currently being processed. Please retry shortly.");
            }
        }

        // Create new idempotency key
        PaymentIdempotencyKey keyEntity = PaymentIdempotencyKey.builder()
                .idempotencyKey(idempotencyKey)
                .operationType(operationType)
                .resourceReference(resourceRef)
                .requestHash(requestHash)
                .status(IdempotencyStatus.PENDING)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();

        idempotencyRepository.save(keyEntity);
        log.debug("Acquired new idempotency key '{}' for operation '{}'", idempotencyKey, operationType);
        return new IdempotencyCheck(IdempotencyResult.NEW, null);
    }

    @Override
    @Transactional
    public void complete(String idempotencyKey, String responseRef) {
        idempotencyRepository.findByIdempotencyKey(idempotencyKey).ifPresent(key -> {
            key.setStatus(IdempotencyStatus.COMPLETED);
            key.setResponseReference(responseRef);
            idempotencyRepository.save(key);
            log.debug("Completed idempotency key '{}' with responseRef '{}'", idempotencyKey, responseRef);
        });
    }

    @Override
    @Transactional
    public void fail(String idempotencyKey) {
        idempotencyRepository.findByIdempotencyKey(idempotencyKey).ifPresent(key -> {
            key.setStatus(IdempotencyStatus.FAILED);
            idempotencyRepository.save(key);
            log.debug("Marked idempotency key '{}' as FAILED", idempotencyKey);
        });
    }

    private String hashPayload(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
