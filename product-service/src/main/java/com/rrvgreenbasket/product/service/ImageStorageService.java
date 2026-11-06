package com.rrvgreenbasket.product.service;

import org.springframework.web.multipart.MultipartFile;

public interface ImageStorageService {

    record StorageResult(String storageKey, String imageUrl, String storageProvider) {}

    StorageResult uploadImage(MultipartFile file, String folderPrefix);

    void deleteImage(String storageKey);

    String getProviderName();
}
