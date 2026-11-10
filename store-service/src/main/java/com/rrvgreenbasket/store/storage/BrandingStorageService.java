package com.rrvgreenbasket.store.storage;

import org.springframework.web.multipart.MultipartFile;

public interface BrandingStorageService {

    record BrandingStorageResult(
            String storageKey,
            String deliveryUrl,
            String originalFileName,
            String storageProvider
    ) {}

    BrandingStorageResult uploadLogo(MultipartFile file);

    BrandingStorageResult uploadFavicon(MultipartFile file);

    void deleteAsset(String storageKey);

    String getProviderName();
}
