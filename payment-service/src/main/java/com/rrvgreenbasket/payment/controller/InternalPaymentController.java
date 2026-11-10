package com.rrvgreenbasket.payment.controller;

import com.rrvgreenbasket.payment.dto.request.CodCollectionRequest;
import com.rrvgreenbasket.payment.dto.response.ApiResponse;
import com.rrvgreenbasket.payment.dto.response.CodCollectionResponse;
import com.rrvgreenbasket.payment.security.SecurityUtils;
import com.rrvgreenbasket.payment.service.CodCollectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/internal/payments")
@RequiredArgsConstructor
@Tag(name = "Internal Payment APIs", description = "Internal service-to-service endpoints for authorized workflows (e.g. Delivery Cash Collections)")
public class InternalPaymentController {

    private final CodCollectionService codCollectionService;

    @PostMapping("/{paymentPublicId}/cod/collect")
    @PreAuthorize("hasAnyAuthority('ROLE_INTERNAL_SERVICE', 'INTERNAL_SERVICE', 'ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
    @Operation(summary = "Record authorized cash collected at delivery for 50% partial COD orders",
            description = "Protected internal endpoint invoked by delivery personnel workflow upon handover. " +
                    "Validates that advance was paid, prevents duplicate collections via idempotency, updates balance, and marks payment PAID.")
    public ResponseEntity<ApiResponse<CodCollectionResponse>> collectCod(
            @PathVariable String paymentPublicId,
            @Valid @RequestBody CodCollectionRequest request) {

        String caller = SecurityUtils.getCurrentUser();
        log.info("Received internal COD collection request for payment '{}' by caller '{}'", paymentPublicId, caller);

        CodCollectionResponse response = codCollectionService.collectCod(paymentPublicId, request, caller);
        return ResponseEntity.ok(ApiResponse.success("COD cash collection recorded successfully", response));
    }
}
