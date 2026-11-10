package com.rrvgreenbasket.store.controller;

import com.rrvgreenbasket.store.dto.ApiResponse;
import com.rrvgreenbasket.store.dto.response.PublicStoreSettingsResponse;
import com.rrvgreenbasket.store.service.StoreSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/store")
@RequiredArgsConstructor
@Tag(name = "Public Store Information", description = "Public endpoints for customers and website visitors to retrieve store contact details, branding, logo, and favicon")
public class PublicStoreController {

    private final StoreSettingsService storeSettingsService;

    @Operation(summary = "Get public store information", description = "Returns public branding information, store name, tagline, support contacts, physical address, logo, and favicon URL without exposing any administrative data.")
    @GetMapping("/settings/public")
    public ResponseEntity<ApiResponse<PublicStoreSettingsResponse>> getPublicStoreSettings() {
        log.debug("Public visitor retrieving store settings");
        PublicStoreSettingsResponse response = storeSettingsService.getPublicStoreSettings();
        return ResponseEntity.ok(ApiResponse.success("Store public settings retrieved successfully", response));
    }
}
