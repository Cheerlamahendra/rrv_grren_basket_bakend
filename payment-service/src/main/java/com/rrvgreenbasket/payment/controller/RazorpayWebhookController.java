package com.rrvgreenbasket.payment.controller;

import com.rrvgreenbasket.payment.dto.response.ApiResponse;
import com.rrvgreenbasket.payment.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhook APIs", description = "Endpoints receiving incoming asynchronous notifications from payment providers like Razorpay")
public class RazorpayWebhookController {

    private final WebhookService webhookService;

    @PostMapping("/razorpay")
    @Operation(summary = "Razorpay Webhook Callback Receiver",
            description = "Receives raw webhook payloads from Razorpay, validates HMAC-SHA256 signature, deduplicates via DB inbox, and processes events asynchronously.")
    public ResponseEntity<ApiResponse<String>> handleRazorpayWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {

        log.info("Incoming Razorpay webhook received. Signature present: {}", signature != null);

        if (signature == null || signature.isBlank()) {
            log.warn("Rejected webhook request: Missing X-Razorpay-Signature header");
            return ResponseEntity.badRequest().body(ApiResponse.error("Missing X-Razorpay-Signature header"));
        }

        webhookService.processRazorpayWebhook(rawPayload, signature);
        return ResponseEntity.ok(ApiResponse.success("Webhook event received and recorded successfully"));
    }
}
