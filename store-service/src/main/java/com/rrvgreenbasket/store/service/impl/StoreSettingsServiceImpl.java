package com.rrvgreenbasket.store.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.store.dto.request.UpdateStoreSettingsRequest;
import com.rrvgreenbasket.store.dto.response.AdminStoreSettingsResponse;
import com.rrvgreenbasket.store.dto.response.AssetUploadResponse;
import com.rrvgreenbasket.store.dto.response.PublicStoreSettingsResponse;
import com.rrvgreenbasket.store.entity.StoreSettings;
import com.rrvgreenbasket.store.entity.StoreSettingsAudit;
import com.rrvgreenbasket.store.enums.StoreAuditAction;
import com.rrvgreenbasket.store.exception.ResourceNotFoundException;
import com.rrvgreenbasket.store.repository.StoreSettingsAuditRepository;
import com.rrvgreenbasket.store.repository.StoreSettingsRepository;
import com.rrvgreenbasket.store.service.StoreSettingsService;
import com.rrvgreenbasket.store.storage.BrandingStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoreSettingsServiceImpl implements StoreSettingsService {

    private final StoreSettingsRepository storeSettingsRepository;
    private final StoreSettingsAuditRepository auditRepository;
    private final BrandingStorageService brandingStorageService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public AdminStoreSettingsResponse getAdminStoreSettings() {
        StoreSettings settings = getOrCreateActiveSettings();
        return mapToAdminResponse(settings);
    }

    @Override
    @Transactional(readOnly = true)
    public PublicStoreSettingsResponse getPublicStoreSettings() {
        StoreSettings settings = getOrCreateActiveSettings();
        return mapToPublicResponse(settings);
    }

    @Override
    @Transactional
    public AdminStoreSettingsResponse updateStoreSettings(UpdateStoreSettingsRequest request, String adminId) {
        log.info("Admin {} updating store general information: storeName='{}'", adminId, request.getStoreName());

        StoreSettings settings = getOrCreateActiveSettings();

        Map<String, String> changes = new HashMap<>();
        if (!settings.getStoreName().equals(request.getStoreName().trim())) {
            changes.put("storeName", request.getStoreName().trim());
        }
        if (!String.valueOf(settings.getBrandTagline()).equals(String.valueOf(request.getBrandTagline()))) {
            changes.put("brandTagline", request.getBrandTagline());
        }
        if (!settings.getSupportEmail().equalsIgnoreCase(request.getSupportEmail().trim())) {
            changes.put("supportEmail", request.getSupportEmail().trim());
        }
        if (!settings.getSupportPhone().equals(request.getSupportPhone().trim())) {
            changes.put("supportPhone", request.getSupportPhone().trim());
        }
        if (!settings.getPhysicalAddress().equals(request.getPhysicalAddress().trim())) {
            changes.put("physicalAddress", request.getPhysicalAddress().trim());
        }
        if (!settings.getWebsiteUrl().equals(request.getWebsiteUrl().trim())) {
            changes.put("websiteUrl", request.getWebsiteUrl().trim());
        }

        // Update fields while strictly preserving existing logo and favicon assets
        settings.setStoreName(request.getStoreName().trim());
        settings.setBrandTagline(request.getBrandTagline() != null ? request.getBrandTagline().trim() : null);
        settings.setSupportEmail(request.getSupportEmail().trim().toLowerCase());
        settings.setSupportPhone(request.getSupportPhone().trim());
        settings.setPhysicalAddress(request.getPhysicalAddress().trim());
        settings.setWebsiteUrl(request.getWebsiteUrl().trim());
        settings.setUpdatedBy(adminId);

        StoreSettings saved = storeSettingsRepository.save(settings);

        recordAudit(saved.getPublicId(), adminId, StoreAuditAction.UPDATE_INFO, toJson(changes));
        log.info("Store settings successfully updated by admin {}", adminId);

        return mapToAdminResponse(saved);
    }

    @Override
    public AssetUploadResponse uploadLogo(MultipartFile file, String adminId) {
        log.info("Admin {} uploading new store logo: fileName={}, size={}",
                adminId, file.getOriginalFilename(), file.getSize());

        StoreSettings settings = getOrCreateActiveSettings();
        String oldLogoS3Key = settings.getLogoS3Key();

        // 1. Upload new asset to AWS S3
        BrandingStorageService.BrandingStorageResult uploadResult = brandingStorageService.uploadLogo(file);

        // 2. Persist update in database safely
        try {
            updateLogoInDatabase(settings.getId(), uploadResult.storageKey(), uploadResult.deliveryUrl(),
                    uploadResult.originalFileName(), adminId);
        } catch (Exception ex) {
            log.error("Database update failed after logo upload. Cleaning up orphaned S3 object: {}", uploadResult.storageKey(), ex);
            brandingStorageService.deleteAsset(uploadResult.storageKey());
            throw ex;
        }

        // 3. Only after DB succeeds, clean up old asset from S3
        if (oldLogoS3Key != null && !oldLogoS3Key.isBlank() && !oldLogoS3Key.equals(uploadResult.storageKey())) {
            log.info("Cleaning up replaced logo from S3: {}", oldLogoS3Key);
            brandingStorageService.deleteAsset(oldLogoS3Key);
        }

        return AssetUploadResponse.forLogo(uploadResult.originalFileName(), uploadResult.deliveryUrl());
    }

    @Override
    public AssetUploadResponse uploadFavicon(MultipartFile file, String adminId) {
        log.info("Admin {} uploading new store favicon: fileName={}, size={}",
                adminId, file.getOriginalFilename(), file.getSize());

        StoreSettings settings = getOrCreateActiveSettings();
        String oldFaviconS3Key = settings.getFaviconS3Key();

        // 1. Upload new favicon to AWS S3
        BrandingStorageService.BrandingStorageResult uploadResult = brandingStorageService.uploadFavicon(file);

        // 2. Persist update in database safely
        try {
            updateFaviconInDatabase(settings.getId(), uploadResult.storageKey(), uploadResult.deliveryUrl(),
                    uploadResult.originalFileName(), adminId);
        } catch (Exception ex) {
            log.error("Database update failed after favicon upload. Cleaning up orphaned S3 object: {}", uploadResult.storageKey(), ex);
            brandingStorageService.deleteAsset(uploadResult.storageKey());
            throw ex;
        }

        // 3. Only after DB succeeds, clean up old asset from S3
        if (oldFaviconS3Key != null && !oldFaviconS3Key.isBlank() && !oldFaviconS3Key.equals(uploadResult.storageKey())) {
            log.info("Cleaning up replaced favicon from S3: {}", oldFaviconS3Key);
            brandingStorageService.deleteAsset(oldFaviconS3Key);
        }

        return AssetUploadResponse.forFavicon(uploadResult.originalFileName(), uploadResult.deliveryUrl());
    }

    @Override
    @Transactional
    public void deleteLogo(String adminId) {
        log.info("Admin {} deleting store logo", adminId);
        StoreSettings settings = getOrCreateActiveSettings();
        String s3Key = settings.getLogoS3Key();

        if (s3Key != null && !s3Key.isBlank()) {
            brandingStorageService.deleteAsset(s3Key);
        }

        settings.setLogoFileName(null);
        settings.setLogoS3Key(null);
        settings.setLogoUrl(null);
        settings.setUpdatedBy(adminId);
        storeSettingsRepository.save(settings);

        recordAudit(settings.getPublicId(), adminId, StoreAuditAction.DELETE_LOGO, "Logo deleted by admin");
        log.info("Store logo deleted successfully by admin {}", adminId);
    }

    @Override
    @Transactional
    public void deleteFavicon(String adminId) {
        log.info("Admin {} deleting store favicon", adminId);
        StoreSettings settings = getOrCreateActiveSettings();
        String s3Key = settings.getFaviconS3Key();

        if (s3Key != null && !s3Key.isBlank()) {
            brandingStorageService.deleteAsset(s3Key);
        }

        settings.setFaviconFileName(null);
        settings.setFaviconS3Key(null);
        settings.setFaviconUrl(null);
        settings.setUpdatedBy(adminId);
        storeSettingsRepository.save(settings);

        recordAudit(settings.getPublicId(), adminId, StoreAuditAction.DELETE_FAVICON, "Favicon deleted by admin");
        log.info("Store favicon deleted successfully by admin {}", adminId);
    }

    @Transactional
    protected void updateLogoInDatabase(Long settingsId, String s3Key, String url, String fileName, String adminId) {
        StoreSettings settings = storeSettingsRepository.findById(settingsId)
                .orElseThrow(() -> new ResourceNotFoundException("Store settings record not found"));

        settings.setLogoFileName(fileName);
        settings.setLogoS3Key(s3Key);
        settings.setLogoUrl(url);
        settings.setUpdatedBy(adminId);

        storeSettingsRepository.save(settings);
        recordAudit(settings.getPublicId(), adminId, StoreAuditAction.UPLOAD_LOGO, "Uploaded logo: " + s3Key);
    }

    @Transactional
    protected void updateFaviconInDatabase(Long settingsId, String s3Key, String url, String fileName, String adminId) {
        StoreSettings settings = storeSettingsRepository.findById(settingsId)
                .orElseThrow(() -> new ResourceNotFoundException("Store settings record not found"));

        settings.setFaviconFileName(fileName);
        settings.setFaviconS3Key(s3Key);
        settings.setFaviconUrl(url);
        settings.setUpdatedBy(adminId);

        storeSettingsRepository.save(settings);
        recordAudit(settings.getPublicId(), adminId, StoreAuditAction.UPLOAD_FAVICON, "Uploaded favicon: " + s3Key);
    }

    private StoreSettings getOrCreateActiveSettings() {
        return storeSettingsRepository.findFirstByIsActiveTrueOrderByUpdatedAtDesc()
                .orElseGet(() -> {
                    log.warn("No active store settings found in database. Initializing default settings.");
                    StoreSettings initial = StoreSettings.builder()
                            .publicId(UUID.randomUUID().toString())
                            .storeName("RRV Green Basket")
                            .brandTagline("Freshness Delivered Daily")
                            .supportEmail("rrvgreenbasket@gmail.com")
                            .supportPhone("+91 98765 43210")
                            .physicalAddress("Kurnool, Andhra Pradesh, India")
                            .websiteUrl("https://www.rrvgreenbasket.in")
                            .isActive(true)
                            .updatedBy("SYSTEM")
                            .build();
                    StoreSettings saved = storeSettingsRepository.save(initial);
                    recordAudit(saved.getPublicId(), "SYSTEM", StoreAuditAction.INITIALIZE, "Initial default settings auto-created");
                    return saved;
                });
    }

    private void recordAudit(String storeSettingsPublicId, String actorId, StoreAuditAction action, String changedFields) {
        StoreSettingsAudit audit = StoreSettingsAudit.builder()
                .storeSettingsPublicId(storeSettingsPublicId)
                .actorId(actorId != null ? actorId : "SYSTEM")
                .actorRole("ADMIN")
                .action(action)
                .changedFields(changedFields)
                .build();
        auditRepository.save(audit);
    }

    private AdminStoreSettingsResponse mapToAdminResponse(StoreSettings s) {
        return AdminStoreSettingsResponse.builder()
                .publicId(s.getPublicId())
                .storeName(s.getStoreName())
                .brandTagline(s.getBrandTagline())
                .supportEmail(s.getSupportEmail())
                .supportPhone(s.getSupportPhone())
                .physicalAddress(s.getPhysicalAddress())
                .websiteUrl(s.getWebsiteUrl())
                .logoFileName(s.getLogoFileName())
                .logoS3Key(s.getLogoS3Key())
                .logoUrl(s.getLogoUrl())
                .faviconFileName(s.getFaviconFileName())
                .faviconS3Key(s.getFaviconS3Key())
                .faviconUrl(s.getFaviconUrl())
                .isActive(s.getIsActive())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .updatedBy(s.getUpdatedBy())
                .build();
    }

    private PublicStoreSettingsResponse mapToPublicResponse(StoreSettings s) {
        return PublicStoreSettingsResponse.builder()
                .storeName(s.getStoreName())
                .brandTagline(s.getBrandTagline())
                .supportEmail(s.getSupportEmail())
                .supportPhone(s.getSupportPhone())
                .physicalAddress(s.getPhysicalAddress())
                .websiteUrl(s.getWebsiteUrl())
                .logoUrl(s.getLogoUrl())
                .faviconUrl(s.getFaviconUrl())
                .build();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }
}
