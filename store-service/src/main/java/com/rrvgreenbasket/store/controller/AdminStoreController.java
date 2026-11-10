package com.rrvgreenbasket.store.controller;

import com.rrvgreenbasket.store.dto.ApiResponse;
import com.rrvgreenbasket.store.dto.request.UpdateStoreSettingsRequest;
import com.rrvgreenbasket.store.dto.response.AdminStoreSettingsResponse;
import com.rrvgreenbasket.store.dto.response.AssetUploadResponse;
import com.rrvgreenbasket.store.security.SecurityUtils;
import com.rrvgreenbasket.store.service.StoreSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/admin/store")
@RequiredArgsConstructor
@Tag(name = "Admin Store Management", description = "Endpoints for administrators to manage general store settings, brand info, platform logo, and favicon in AWS S3")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPER_ADMIN', 'ADMIN')")
public class AdminStoreController {

    private final StoreSettingsService storeSettingsService;

    @Operation(summary = "Get current store settings", description = "Returns full store settings, contact information, platform logo, and favicon details for the admin portal.")
    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<AdminStoreSettingsResponse>> getStoreSettings() {
        log.debug("Admin requesting store settings");
        AdminStoreSettingsResponse response = storeSettingsService.getAdminStoreSettings();
        return ResponseEntity.ok(ApiResponse.success("Store settings retrieved successfully", response));
    }

    @Operation(summary = "Update store general information", description = "Updates platform name, brand tagline, support contact, and physical address while strictly preserving existing logo and favicon assets.")
    @PutMapping("/settings")
    public ResponseEntity<ApiResponse<AdminStoreSettingsResponse>> updateStoreSettings(
            @Valid @RequestBody UpdateStoreSettingsRequest request) {

        String adminId = SecurityUtils.getCurrentUserId();
        if (adminId == null || adminId.isBlank()) {
            adminId = "ADMIN";
        }

        log.info("Admin {} updating store settings: name={}", adminId, request.getStoreName());
        AdminStoreSettingsResponse response = storeSettingsService.updateStoreSettings(request, adminId);
        return ResponseEntity.ok(ApiResponse.success("Store settings updated successfully", response));
    }

    @Operation(summary = "Upload platform logo to AWS S3", description = "Uploads store logo (PNG, JPG, JPEG, WEBP, SVG max 2MB) to AWS S3 branding/logo/ prefix and saves public delivery URL in store settings.")
    @PostMapping(value = "/assets/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AssetUploadResponse>> uploadLogo(
            @Parameter(description = "Logo image file (PNG, JPG, JPEG, WEBP, SVG, max 2MB)", required = true)
            @RequestParam("file") MultipartFile file) {

        String adminId = SecurityUtils.getCurrentUserId();
        if (adminId == null || adminId.isBlank()) {
            adminId = "ADMIN";
        }

        log.info("Admin {} uploading logo: name={}, size={}", adminId, file.getOriginalFilename(), file.getSize());
        AssetUploadResponse response = storeSettingsService.uploadLogo(file, adminId);
        return ResponseEntity.ok(ApiResponse.success("Logo uploaded successfully", response));
    }

    @Operation(summary = "Upload app favicon to AWS S3", description = "Uploads application favicon (ICO, PNG max 2MB) to AWS S3 branding/favicon/ prefix and saves public delivery URL in store settings.")
    @PostMapping(value = "/assets/favicon", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AssetUploadResponse>> uploadFavicon(
            @Parameter(description = "Favicon image file (ICO, PNG, max 2MB)", required = true)
            @RequestParam("file") MultipartFile file) {

        String adminId = SecurityUtils.getCurrentUserId();
        if (adminId == null || adminId.isBlank()) {
            adminId = "ADMIN";
        }

        log.info("Admin {} uploading favicon: name={}, size={}", adminId, file.getOriginalFilename(), file.getSize());
        AssetUploadResponse response = storeSettingsService.uploadFavicon(file, adminId);
        return ResponseEntity.ok(ApiResponse.success("Favicon uploaded successfully", response));
    }

    @Operation(summary = "Delete platform logo", description = "Deletes platform logo from AWS S3 and clears URL in store settings.")
    @DeleteMapping("/assets/logo")
    public ResponseEntity<ApiResponse<Void>> deleteLogo() {
        String adminId = SecurityUtils.getCurrentUserId();
        if (adminId == null || adminId.isBlank()) {
            adminId = "ADMIN";
        }

        log.info("Admin {} deleting store logo", adminId);
        storeSettingsService.deleteLogo(adminId);
        return ResponseEntity.ok(ApiResponse.success("Platform logo deleted successfully"));
    }

    @Operation(summary = "Delete app favicon", description = "Deletes app favicon from AWS S3 and clears URL in store settings.")
    @DeleteMapping("/assets/favicon")
    public ResponseEntity<ApiResponse<Void>> deleteFavicon() {
        String adminId = SecurityUtils.getCurrentUserId();
        if (adminId == null || adminId.isBlank()) {
            adminId = "ADMIN";
        }

        log.info("Admin {} deleting store favicon", adminId);
        storeSettingsService.deleteFavicon(adminId);
        return ResponseEntity.ok(ApiResponse.success("App favicon deleted successfully"));
    }
}
