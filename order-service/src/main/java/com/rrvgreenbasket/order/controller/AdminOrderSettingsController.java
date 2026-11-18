package com.rrvgreenbasket.order.controller;

import com.rrvgreenbasket.order.dto.request.UpdateOrderBusinessSettingsRequest;
import com.rrvgreenbasket.order.dto.request.UpdateSettingsStatusRequest;
import com.rrvgreenbasket.order.dto.response.ApiResponse;
import com.rrvgreenbasket.order.dto.response.OrderBusinessSettingsResponse;
import com.rrvgreenbasket.order.security.UserPrincipal;
import com.rrvgreenbasket.order.service.OrderBusinessSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/admin/orders/settings")
@RequiredArgsConstructor
@Tag(name = "Admin Order Settings", description = "Admin configuration for daily ordering window, cutoffs, and delivery schedule")
public class AdminOrderSettingsController {

    private final OrderBusinessSettingsService businessSettingsService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Get current order business settings")
    public ResponseEntity<ApiResponse<OrderBusinessSettingsResponse>> getSettings() {
        log.info("Admin query order settings");
        OrderBusinessSettingsResponse settings = businessSettingsService.getSettings();
        return ResponseEntity.ok(ApiResponse.success("Order settings fetched successfully", settings));
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Update order business settings (opening/closing hours, delivery offset, timezone)")
    public ResponseEntity<ApiResponse<OrderBusinessSettingsResponse>> updateSettings(
            @Valid @RequestBody UpdateOrderBusinessSettingsRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        String adminUser = (principal != null) ? principal.getUsername() : "ADMIN";
        log.info("Admin '{}' updating order settings", adminUser);
        OrderBusinessSettingsResponse updated = businessSettingsService.updateSettings(request, adminUser);
        return ResponseEntity.ok(ApiResponse.success("Order settings updated successfully", updated));
    }

    @PatchMapping("/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Toggle order acceptance status enabled/disabled")
    public ResponseEntity<ApiResponse<OrderBusinessSettingsResponse>> updateStatus(
            @Valid @RequestBody UpdateSettingsStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        String adminUser = (principal != null) ? principal.getUsername() : "ADMIN";
        log.info("Admin '{}' toggling order acceptance enabled to: {}", adminUser, request.getOrderAcceptanceEnabled());
        OrderBusinessSettingsResponse updated = businessSettingsService.updateOrderAcceptanceStatus(
                request.getOrderAcceptanceEnabled(), adminUser
        );
        return ResponseEntity.ok(ApiResponse.success("Order acceptance status updated", updated));
    }
}
