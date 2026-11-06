package com.rrvgreenbasket.product.service.impl;

import com.rrvgreenbasket.product.exception.FileUploadException;
import com.rrvgreenbasket.product.service.ImageStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(name = "aws.s3.provider", havingValue = "mock", matchIfMissing = true)
public class MockImageStorageService implements ImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(MockImageStorageService.class);

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB
    private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
    );

    private final String cloudfrontDomain;
    private final Set<String> storedKeys = ConcurrentHashMap.newKeySet();
    private final List<String> deletedKeys = new java.util.concurrent.CopyOnWriteArrayList<>();
    private volatile boolean simulateUploadFailure = false;

    public MockImageStorageService(@Value("${aws.s3.cloudfront-domain:https://cdn.rrvgreenbasket.com}") String cloudfrontDomain) {
        this.cloudfrontDomain = cloudfrontDomain.replaceAll("/+$", "");
    }

    public void setSimulateUploadFailure(boolean simulate) {
        this.simulateUploadFailure = simulate;
    }

    public List<String> getDeletedKeys() {
        return deletedKeys;
    }

    @Override
    public StorageResult uploadImage(MultipartFile file, String folderPrefix) {
        validateFile(file);

        if (simulateUploadFailure) {
            throw new FileUploadException("Simulated AWS S3 upload failure (network/timeout)");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }

        String uniqueFileName = UUID.randomUUID() + extension;
        String prefix = (folderPrefix != null && !folderPrefix.isBlank())
                ? folderPrefix.replaceAll("^/+|/+$", "") + "/"
                : "products/";

        String storageKey = prefix + uniqueFileName;
        String imageUrl = cloudfrontDomain + "/" + storageKey;

        storedKeys.add(storageKey);
        log.info("[MockStorage] Successfully uploaded image to mock storage: key={}, url={}", storageKey, imageUrl);

        return new StorageResult(storageKey, imageUrl, "mock");
    }

    @Override
    public void deleteImage(String storageKey) {
        if (storageKey != null) {
            storedKeys.remove(storageKey);
            deletedKeys.add(storageKey);
            log.info("[MockStorage] Successfully deleted image from mock storage: key={}", storageKey);
        }
    }

    @Override
    public String getProviderName() {
        return "mock";
    }

    public boolean hasStoredKey(String storageKey) {
        return storedKeys.contains(storageKey);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileUploadException("File cannot be empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new FileUploadException("File size exceeds maximum allowed limit of 5 MB (actual: " + file.getSize() + " bytes)");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new FileUploadException("Invalid file type: " + contentType + ". Allowed types: JPEG, JPG, PNG, WebP");
        }
    }
}
