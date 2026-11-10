package com.rrvgreenbasket.store.storage;

import com.rrvgreenbasket.store.enums.AssetType;
import com.rrvgreenbasket.store.exception.FileUploadException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@ConditionalOnProperty(name = "aws.s3.provider", havingValue = "mock", matchIfMissing = true)
public class MockBrandingStorageService implements BrandingStorageService {

    private final String cloudfrontDomain;
    private final Set<String> storedKeys = ConcurrentHashMap.newKeySet();
    private final List<String> deletedKeys = new CopyOnWriteArrayList<>();
    private volatile boolean simulateFailure = false;

    public MockBrandingStorageService(
            @Value("${aws.s3.cloudfront-domain:https://cdn.rrvgreenbasket.com}") String cloudfrontDomain) {
        this.cloudfrontDomain = cloudfrontDomain.replaceAll("/+$", "");
    }

    public void setSimulateFailure(boolean simulate) {
        this.simulateFailure = simulate;
    }

    public List<String> getDeletedKeys() {
        return deletedKeys;
    }

    public boolean hasStoredKey(String key) {
        return storedKeys.contains(key);
    }

    @Override
    public BrandingStorageResult uploadLogo(MultipartFile file) {
        AssetValidationUtil.validateAsset(file, AssetType.LOGO);
        if (simulateFailure) {
            throw new FileUploadException("Simulated AWS S3 upload failure (e.g. S3 ServiceUnavailable)");
        }

        String extension = getExtension(file.getOriginalFilename());
        String objectKey = "branding/logo/rrv-green-basket-logo-" + UUID.randomUUID() + extension;
        String deliveryUrl = cloudfrontDomain + "/" + objectKey;

        storedKeys.add(objectKey);
        log.info("[MockStorage] Uploaded logo: Key={}, Url={}", objectKey, deliveryUrl);

        return new BrandingStorageResult(objectKey, deliveryUrl, file.getOriginalFilename(), "mock");
    }

    @Override
    public BrandingStorageResult uploadFavicon(MultipartFile file) {
        AssetValidationUtil.validateAsset(file, AssetType.FAVICON);
        if (simulateFailure) {
            throw new FileUploadException("Simulated AWS S3 upload failure");
        }

        String extension = getExtension(file.getOriginalFilename());
        String objectKey = "branding/favicon/rrv-green-basket-favicon-" + UUID.randomUUID() + extension;
        String deliveryUrl = cloudfrontDomain + "/" + objectKey;

        storedKeys.add(objectKey);
        log.info("[MockStorage] Uploaded favicon: Key={}, Url={}", objectKey, deliveryUrl);

        return new BrandingStorageResult(objectKey, deliveryUrl, file.getOriginalFilename(), "mock");
    }

    @Override
    public void deleteAsset(String storageKey) {
        if (storageKey != null && !storageKey.isBlank()) {
            storedKeys.remove(storageKey);
            deletedKeys.add(storageKey);
            log.info("[MockStorage] Deleted asset: Key={}", storageKey);
        }
    }

    @Override
    public String getProviderName() {
        return "mock";
    }

    private String getExtension(String filename) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf(".")).toLowerCase(Locale.ROOT);
        }
        return ".png";
    }
}
