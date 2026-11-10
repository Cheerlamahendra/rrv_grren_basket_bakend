package com.rrvgreenbasket.payment.service;

public interface IdempotencyService {

    enum IdempotencyResult {
        NEW,
        ALREADY_COMPLETED,
        IN_PROGRESS
    }

    record IdempotencyCheck(IdempotencyResult result, String responseReference) {}

    IdempotencyCheck checkAndAcquire(String idempotencyKey, String operationType, String resourceRef, String requestPayload);

    void complete(String idempotencyKey, String responseRef);

    void fail(String idempotencyKey);
}
