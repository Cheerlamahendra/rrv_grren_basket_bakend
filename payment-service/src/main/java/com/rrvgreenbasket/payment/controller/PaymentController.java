package com.rrvgreenbasket.payment.controller;

import com.rrvgreenbasket.payment.dto.request.OnlinePaymentInitiateRequest;
import com.rrvgreenbasket.payment.dto.request.PartialCodPaymentInitiateRequest;
import com.rrvgreenbasket.payment.dto.request.PaymentVerificationRequest;
import com.rrvgreenbasket.payment.dto.response.*;
import com.rrvgreenbasket.payment.security.SecurityUtils;
import com.rrvgreenbasket.payment.service.OnlinePaymentService;
import com.rrvgreenbasket.payment.service.PartialPaymentService;
import com.rrvgreenbasket.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Customer Payment APIs", description = "Endpoints for initiating, verifying, and tracking customer payments (Full Online & 50% Advance Partial COD)")
public class PaymentController {

    private final OnlinePaymentService onlinePaymentService;
    private final PartialPaymentService partialPaymentService;
    private final PaymentService paymentService;

    @PostMapping("/online/initiate")
    @Operation(summary = "Initiate 100% full online payment via Razorpay",
            description = "Creates a Razorpay order for 100% of the trusted order amount and returns checkout initialization details.")
    public ResponseEntity<ApiResponse<OnlinePaymentInitiateResponse>> initiateOnlinePayment(
            @Valid @RequestBody OnlinePaymentInitiateRequest request) {

        String customerId = SecurityUtils.getCurrentUserId();
        OnlinePaymentInitiateResponse response = onlinePaymentService.initiatePayment(request, customerId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Online payment initiated successfully", response));
    }

    @PostMapping("/partial-cod/initiate")
    @Operation(summary = "Initiate 50% online advance + 50% Cash on Delivery payment",
            description = "Calculates the 50% advance and 50% delivery cash balance, creates a Razorpay order for ONLY the 50% advance, and returns checkout details.")
    public ResponseEntity<ApiResponse<PartialCodPaymentInitiateResponse>> initiatePartialCodPayment(
            @Valid @RequestBody PartialCodPaymentInitiateRequest request) {

        String customerId = SecurityUtils.getCurrentUserId();
        PartialCodPaymentInitiateResponse response = partialPaymentService.initiatePartialCod(request, customerId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Partial COD payment initiated successfully", response));
    }

    @PostMapping("/online/verify")
    @Operation(summary = "Verify online checkout payment (Full or Advance)",
            description = "Performs server-side HMAC-SHA256 signature verification and verifies captured status with Razorpay.")
    public ResponseEntity<ApiResponse<PaymentResponse>> verifyOnlinePayment(
            @Valid @RequestBody PaymentVerificationRequest request) {

        String customerId = SecurityUtils.getCurrentUserId();
        PaymentResponse response = onlinePaymentService.verifyPayment(request, customerId);
        return ResponseEntity.ok(ApiResponse.success("Payment verified successfully", response));
    }

    @GetMapping("/{paymentPublicId}")
    @Operation(summary = "Get payment details by public ID")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentDetails(
            @PathVariable String paymentPublicId) {

        String customerId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.isAdmin();
        PaymentResponse response = paymentService.getPaymentDetails(paymentPublicId, customerId, isAdmin);
        return ResponseEntity.ok(ApiResponse.success("Payment details retrieved successfully", response));
    }

    @GetMapping("/{paymentPublicId}/status")
    @Operation(summary = "Get lightweight payment status")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> getPaymentStatus(
            @PathVariable String paymentPublicId) {

        String customerId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.isAdmin();
        PaymentStatusResponse response = paymentService.getPaymentStatus(paymentPublicId, customerId, isAdmin);
        return ResponseEntity.ok(ApiResponse.success("Payment status retrieved successfully", response));
    }

    @GetMapping
    @Operation(summary = "Get paginated payment history for the authenticated customer")
    public ResponseEntity<ApiResponse<PageResponse<PaymentHistoryResponse>>> getCustomerPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        String customerId = SecurityUtils.getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        PageResponse<PaymentHistoryResponse> response = paymentService.getCustomerPaymentHistory(customerId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Payment history retrieved successfully", response));
    }

    @PostMapping("/{paymentPublicId}/retry")
    @Operation(summary = "Retry a failed or pending payment")
    public ResponseEntity<ApiResponse<OnlinePaymentInitiateResponse>> retryPayment(
            @PathVariable String paymentPublicId) {

        String customerId = SecurityUtils.getCurrentUserId();
        OnlinePaymentInitiateResponse response = paymentService.retryPayment(paymentPublicId, customerId);
        return ResponseEntity.ok(ApiResponse.success("Payment retry initiated successfully", response));
    }

    @GetMapping("/{paymentPublicId}/refunds")
    @Operation(summary = "Get refund status and history for a payment")
    public ResponseEntity<ApiResponse<List<PaymentRefundResponse>>> getPaymentRefunds(
            @PathVariable String paymentPublicId) {

        String customerId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.isAdmin();
        List<PaymentRefundResponse> response = paymentService.getPaymentRefunds(paymentPublicId, customerId, isAdmin);
        return ResponseEntity.ok(ApiResponse.success("Refund records retrieved successfully", response));
    }
}
