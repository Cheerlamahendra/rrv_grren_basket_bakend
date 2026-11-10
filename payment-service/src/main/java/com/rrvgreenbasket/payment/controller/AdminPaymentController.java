package com.rrvgreenbasket.payment.controller;

import com.rrvgreenbasket.payment.dto.request.AdminRefundRequest;
import com.rrvgreenbasket.payment.dto.request.PaymentFilterRequest;
import com.rrvgreenbasket.payment.dto.response.*;
import com.rrvgreenbasket.payment.enums.PaymentMethod;
import com.rrvgreenbasket.payment.enums.PaymentStatus;
import com.rrvgreenbasket.payment.security.SecurityUtils;
import com.rrvgreenbasket.payment.service.PaymentService;
import com.rrvgreenbasket.payment.service.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/admin/payments")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPER_ADMIN', 'ADMIN')")
@Tag(name = "Admin Payment APIs", description = "Administrative endpoints for monitoring payments, viewing statistics, transaction history, collections, and executing refunds.")
public class AdminPaymentController {

    private final PaymentService paymentService;
    private final RefundService refundService;

    @GetMapping
    @Operation(summary = "List and filter all payments with pagination",
            description = "Supports filtering by status, method, order ID, customer ID, and date range.")
    public ResponseEntity<ApiResponse<PageResponse<PaymentHistoryResponse>>> getPayments(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) PaymentMethod method,
            @RequestParam(required = false) String orderPublicId,
            @RequestParam(required = false) String customerPublicId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {

        PaymentFilterRequest filter = PaymentFilterRequest.builder()
                .status(status)
                .method(method)
                .orderPublicId(orderPublicId)
                .customerPublicId(customerPublicId)
                .startDate(startDate)
                .endDate(endDate)
                .build();

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        PageResponse<PaymentHistoryResponse> response = paymentService.getAdminPayments(filter, pageable);
        return ResponseEntity.ok(ApiResponse.success("Payments retrieved successfully", response));
    }

    @GetMapping("/{paymentPublicId}")
    @Operation(summary = "Get complete administrative payment details including transactions")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentDetails(
            @PathVariable String paymentPublicId) {

        PaymentResponse response = paymentService.getPaymentDetails(paymentPublicId, null, true);
        return ResponseEntity.ok(ApiResponse.success("Payment details retrieved successfully", response));
    }

    @GetMapping("/statistics")
    @Operation(summary = "Get platform-wide payment statistics",
            description = "Returns order totals, online revenue, partial COD counts, advance collected, cash payable, cash collected, and outstanding balances.")
    public ResponseEntity<ApiResponse<PaymentStatisticsResponse>> getStatistics() {
        PaymentStatisticsResponse response = paymentService.getPaymentStatistics();
        return ResponseEntity.ok(ApiResponse.success("Payment statistics calculated successfully", response));
    }

    @GetMapping("/{paymentPublicId}/history")
    @Operation(summary = "Get state transition history and audit trail for a payment")
    public ResponseEntity<ApiResponse<List<PaymentStatusHistoryResponse>>> getStatusHistory(
            @PathVariable String paymentPublicId) {

        List<PaymentStatusHistoryResponse> response = paymentService.getPaymentStatusHistory(paymentPublicId);
        return ResponseEntity.ok(ApiResponse.success("Status history retrieved successfully", response));
    }

    @GetMapping("/{paymentPublicId}/transactions")
    @Operation(summary = "Get all financial transaction records for a payment")
    public ResponseEntity<ApiResponse<List<PaymentTransactionResponse>>> getTransactions(
            @PathVariable String paymentPublicId) {

        List<PaymentTransactionResponse> response = paymentService.getPaymentTransactions(paymentPublicId);
        return ResponseEntity.ok(ApiResponse.success("Transactions retrieved successfully", response));
    }

    @PostMapping("/{paymentPublicId}/refund")
    @Operation(summary = "Initiate full or partial refund via Razorpay",
            description = "Processes an authorized refund against eligible captured online amounts. Validates against already refunded amounts and records transaction.")
    public ResponseEntity<ApiResponse<PaymentRefundResponse>> processRefund(
            @PathVariable String paymentPublicId,
            @Valid @RequestBody AdminRefundRequest request) {

        String adminUser = SecurityUtils.getCurrentUser();
        log.info("Admin refund requested for payment '{}' by admin '{}'", paymentPublicId, adminUser);

        PaymentRefundResponse response = refundService.processRefund(paymentPublicId, request, adminUser);
        return ResponseEntity.ok(ApiResponse.success("Refund processed successfully via Razorpay", response));
    }

    @GetMapping("/refunds")
    @Operation(summary = "List payment refunds for a specific payment")
    public ResponseEntity<ApiResponse<List<PaymentRefundResponse>>> getRefunds(
            @RequestParam String paymentPublicId) {

        List<PaymentRefundResponse> response = paymentService.getPaymentRefunds(paymentPublicId, null, true);
        return ResponseEntity.ok(ApiResponse.success("Refunds retrieved successfully", response));
    }

    @GetMapping("/cod/collections")
    @Operation(summary = "List all recorded COD cash collections with delivery references")
    public ResponseEntity<ApiResponse<PageResponse<CodCollectionResponse>>> getCodCollections(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("collectedAt").descending());
        PageResponse<CodCollectionResponse> response = paymentService.getAllCodCollections(pageable);
        return ResponseEntity.ok(ApiResponse.success("COD collections retrieved successfully", response));
    }
}
