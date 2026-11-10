package com.rrvgreenbasket.store.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.store.dto.request.UpdateStoreSettingsRequest;
import com.rrvgreenbasket.store.dto.response.AdminStoreSettingsResponse;
import com.rrvgreenbasket.store.dto.response.AssetUploadResponse;
import com.rrvgreenbasket.store.dto.response.PublicStoreSettingsResponse;
import com.rrvgreenbasket.store.entity.StoreSettings;
import com.rrvgreenbasket.store.enums.StoreAuditAction;
import com.rrvgreenbasket.store.repository.StoreSettingsAuditRepository;
import com.rrvgreenbasket.store.repository.StoreSettingsRepository;
import com.rrvgreenbasket.store.service.impl.StoreSettingsServiceImpl;
import com.rrvgreenbasket.store.storage.BrandingStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreSettingsServiceTest {

    @Mock
    private StoreSettingsRepository storeSettingsRepository;

    @Mock
    private StoreSettingsAuditRepository auditRepository;

    @Mock
    private BrandingStorageService brandingStorageService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private StoreSettingsServiceImpl storeSettingsService;

    private StoreSettings existingSettings;

    @BeforeEach
    void setUp() {
        existingSettings = StoreSettings.builder()
                .id(1L)
                .publicId("store-uuid-1111")
                .storeName("RRV Green Basket")
                .brandTagline("Freshness Delivered Daily")
                .supportEmail("rrvgreenbasket@gmail.com")
                .supportPhone("+91 98765 43210")
                .physicalAddress("Kurnool, Andhra Pradesh, India")
                .websiteUrl("https://www.rrvgreenbasket.in")
                .logoFileName("old-logo.png")
                .logoS3Key("branding/logo/old-logo-key.png")
                .logoUrl("https://cdn.rrvgreenbasket.com/branding/logo/old-logo-key.png")
                .faviconFileName("old-favicon.ico")
                .faviconS3Key("branding/favicon/old-favicon-key.ico")
                .faviconUrl("https://cdn.rrvgreenbasket.com/branding/favicon/old-favicon-key.ico")
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .updatedBy("SYSTEM")
                .build();
    }

    @Test
    @DisplayName("Admin gets store settings - Returns full details including S3 assets")
    void testGetAdminStoreSettings() {
        when(storeSettingsRepository.findFirstByIsActiveTrueOrderByUpdatedAtDesc())
                .thenReturn(Optional.of(existingSettings));

        AdminStoreSettingsResponse response = storeSettingsService.getAdminStoreSettings();

        assertThat(response).isNotNull();
        assertThat(response.getStoreName()).isEqualTo("RRV Green Basket");
        assertThat(response.getLogoS3Key()).isEqualTo("branding/logo/old-logo-key.png");
        assertThat(response.getFaviconUrl()).contains("old-favicon-key.ico");
    }

    @Test
    @DisplayName("Public user gets store settings - Returns only public fields")
    void testGetPublicStoreSettings() {
        when(storeSettingsRepository.findFirstByIsActiveTrueOrderByUpdatedAtDesc())
                .thenReturn(Optional.of(existingSettings));

        PublicStoreSettingsResponse response = storeSettingsService.getPublicStoreSettings();

        assertThat(response).isNotNull();
        assertThat(response.getStoreName()).isEqualTo("RRV Green Basket");
        assertThat(response.getLogoUrl()).isEqualTo("https://cdn.rrvgreenbasket.com/branding/logo/old-logo-key.png");
        assertThat(response.getFaviconUrl()).isEqualTo("https://cdn.rrvgreenbasket.com/branding/favicon/old-favicon-key.ico");
    }

    @Test
    @DisplayName("Admin updates store settings - Text updated, existing assets preserved")
    void testUpdateStoreSettings_PreservesAssets() {
        when(storeSettingsRepository.findFirstByIsActiveTrueOrderByUpdatedAtDesc())
                .thenReturn(Optional.of(existingSettings));
        when(storeSettingsRepository.save(any(StoreSettings.class))).thenAnswer(i -> i.getArgument(0));

        UpdateStoreSettingsRequest request = UpdateStoreSettingsRequest.builder()
                .storeName("RRV Green Basket Premium")
                .brandTagline("Organic & Farm Fresh Daily")
                .supportEmail("support@rrvgreenbasket.in")
                .supportPhone("+91 91234 56789")
                .physicalAddress("Hyderabad, Telangana, India")
                .websiteUrl("https://rrvgreenbasket.in")
                .build();

        AdminStoreSettingsResponse response = storeSettingsService.updateStoreSettings(request, "ADMIN_001");

        assertThat(response).isNotNull();
        assertThat(response.getStoreName()).isEqualTo("RRV Green Basket Premium");
        assertThat(response.getSupportEmail()).isEqualTo("support@rrvgreenbasket.in");
        // Verify logo and favicon were strictly preserved
        assertThat(response.getLogoFileName()).isEqualTo("old-logo.png");
        assertThat(response.getLogoS3Key()).isEqualTo("branding/logo/old-logo-key.png");
        assertThat(response.getFaviconS3Key()).isEqualTo("branding/favicon/old-favicon-key.ico");
        verify(auditRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Admin uploads new logo - Uploads to S3, updates DB, cleans up old S3 object")
    void testUploadLogo_CleansUpOldAsset() {
        byte[] validPngHeader = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "new-logo.png", "image/png", validPngHeader);

        when(storeSettingsRepository.findFirstByIsActiveTrueOrderByUpdatedAtDesc())
                .thenReturn(Optional.of(existingSettings));
        when(storeSettingsRepository.findById(1L)).thenReturn(Optional.of(existingSettings));
        when(storeSettingsRepository.save(any(StoreSettings.class))).thenReturn(existingSettings);

        BrandingStorageService.BrandingStorageResult uploadResult =
                new BrandingStorageService.BrandingStorageResult(
                        "branding/logo/rrv-green-basket-logo-new.png",
                        "https://cdn.rrvgreenbasket.com/branding/logo/rrv-green-basket-logo-new.png",
                        "new-logo.png",
                        "mock"
                );
        when(brandingStorageService.uploadLogo(file)).thenReturn(uploadResult);

        AssetUploadResponse response = storeSettingsService.uploadLogo(file, "ADMIN_001");

        assertThat(response).isNotNull();
        assertThat(response.getLogoUrl()).contains("rrv-green-basket-logo-new.png");
        // Verify old logo key was deleted from S3
        verify(brandingStorageService, times(1)).deleteAsset("branding/logo/old-logo-key.png");
    }

    @Test
    @DisplayName("Admin deletes logo - Deletes from S3 and clears DB fields")
    void testDeleteLogo() {
        when(storeSettingsRepository.findFirstByIsActiveTrueOrderByUpdatedAtDesc())
                .thenReturn(Optional.of(existingSettings));
        when(storeSettingsRepository.save(any(StoreSettings.class))).thenReturn(existingSettings);

        storeSettingsService.deleteLogo("ADMIN_001");

        assertThat(existingSettings.getLogoS3Key()).isNull();
        assertThat(existingSettings.getLogoUrl()).isNull();
        assertThat(existingSettings.getLogoFileName()).isNull();
        verify(brandingStorageService, times(1)).deleteAsset("branding/logo/old-logo-key.png");
        verify(auditRepository, times(1)).save(any());
    }
}
