package com.rrvgreenbasket.store.service;

import com.rrvgreenbasket.store.dto.request.UpdateStoreSettingsRequest;
import com.rrvgreenbasket.store.dto.response.AdminStoreSettingsResponse;
import com.rrvgreenbasket.store.dto.response.AssetUploadResponse;
import com.rrvgreenbasket.store.dto.response.PublicStoreSettingsResponse;
import org.springframework.web.multipart.MultipartFile;

public interface StoreSettingsService {

    AdminStoreSettingsResponse getAdminStoreSettings();

    PublicStoreSettingsResponse getPublicStoreSettings();

    AdminStoreSettingsResponse updateStoreSettings(UpdateStoreSettingsRequest request, String adminId);

    AssetUploadResponse uploadLogo(MultipartFile file, String adminId);

    AssetUploadResponse uploadFavicon(MultipartFile file, String adminId);

    void deleteLogo(String adminId);

    void deleteFavicon(String adminId);
}
