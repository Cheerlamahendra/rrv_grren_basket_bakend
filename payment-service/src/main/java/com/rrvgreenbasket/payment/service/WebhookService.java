package com.rrvgreenbasket.payment.service;

public interface WebhookService {

    void processRazorpayWebhook(String rawPayload, String signature);
}
