
package com.rrvgreenbasket.product.service.impl;

import com.rrvgreenbasket.product.exception.FileUploadException;
import com.rrvgreenbasket.product.service.ImageStorageService;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@ConditionalOnProperty(
        name = "aws.s3.provider",
        havingValue = "aws-s3"
)
public class AwsS3ImageStorageService implements ImageStorageService {

    private static final Logger log =
            LoggerFactory.getLogger(AwsS3ImageStorageService.class);

    private static final String S3_FOLDER_PREFIX = "products";

    private final String bucketName;
    private final String cloudfrontDomain;
    private final String defaultFolder;
    private final long maxFileSize;
    private final List<String> allowedContentTypes;

    private final S3Client s3Client;

    public AwsS3ImageStorageService(AwsS3Properties properties) {

        this.bucketName = properties.getBucketName();

        this.cloudfrontDomain = normalizeCloudFrontDomain(
                properties.getCloudfrontDomain()
        );

        this.defaultFolder = properties.getDefaultFolder() == null
                || properties.getDefaultFolder().isBlank()
                ? S3_FOLDER_PREFIX
                : properties.getDefaultFolder()
                  .replaceAll("^/+|/+$", "");

        this.maxFileSize = properties.getMaxFileSizeBytes();

        this.allowedContentTypes = properties.getAllowedContentTypes();

        if (bucketName == null || bucketName.isBlank()) {
            throw new IllegalStateException(
                    "AWS S3 bucket name is not configured"
            );
        }

        if (properties.getRegion() == null
                || properties.getRegion().isBlank()) {
            throw new IllegalStateException(
                    "AWS S3 region is not configured"
            );
        }

        this.s3Client = S3Client.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        log.info("AWS S3 Image Storage initialized");
        log.info("S3 Bucket: {}", bucketName);
        log.info("AWS Region: {}", properties.getRegion());
        log.info("CloudFront Domain configured: {}",
                cloudfrontDomain != null);
        log.info("S3 Default Folder: {}", defaultFolder);
    }

    @Override
    public StorageResult uploadImage(
            MultipartFile file,
            String folderPrefix
    ) {

        validateFile(file);

        String extension = getFileExtension(file.getContentType());

        String uniqueFileName = UUID.randomUUID() + extension;

        String prefix = (folderPrefix != null && !folderPrefix.isBlank())
                ? folderPrefix.replaceAll("^/+|/+$", "")
                : defaultFolder;

        String storageKey = prefix + "/" + uniqueFileName;

        try (InputStream inputStream = file.getInputStream()) {

            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storageKey)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(
                    putRequest,
                    RequestBody.fromInputStream(
                            inputStream,
                            file.getSize()
                    )
            );

            log.info(
                    "Successfully uploaded image to S3. Bucket={}, Key={}",
                    bucketName,
                    storageKey
            );

            // Generate CloudFront URL.
            // Origin Path is configured as /products.
            String imageUrl = generateCloudFrontUrl(storageKey);

            return new StorageResult(
                    storageKey,
                    imageUrl,
                    "aws-s3"
            );

        } catch (IOException e) {

            log.error(
                    "Failed to read image file. Key={}",
                    storageKey,
                    e
            );

            throw new FileUploadException(
                    "Failed to read uploaded image",
                    e
            );

        } catch (S3Exception e) {

            log.error(
                    "AWS S3 error. Status={}, Key={}, Error={}",
                    e.statusCode(),
                    storageKey,
                    e.awsErrorDetails() != null
                            ? e.awsErrorDetails().errorCode()
                            : "UNKNOWN",
                    e
            );

            throw new FileUploadException(
                    "AWS S3 upload failed. Check credentials and IAM permissions.",
                    e
            );

        } catch (Exception e) {

            log.error(
                    "Unexpected AWS S3 upload error. Bucket={}, Key={}",
                    bucketName,
                    storageKey,
                    e
            );

            throw new FileUploadException(
                    "Failed to upload image to AWS S3",
                    e
            );
        }
    }

    /**
     * Generates the CloudFront delivery URL.
     *
     * CloudFront Origin Path = /products
     *
     * S3 key:
     * products/product-id/image.jpg
     *
     * CloudFront URL:
     * https://domain/product-id/image.jpg
     */
    private String generateCloudFrontUrl(String storageKey) {

        if (cloudfrontDomain == null) {
            log.warn(
                    "CloudFront domain is not configured. Returning null image URL."
            );
            return null;
        }

        String viewerPath = storageKey;

        String prefix = S3_FOLDER_PREFIX + "/";

        if (viewerPath.startsWith(prefix)) {
            viewerPath = viewerPath.substring(prefix.length());
        }

        return cloudfrontDomain + "/" + viewerPath;
    }

    private String normalizeCloudFrontDomain(String domain) {

        if (domain == null || domain.isBlank()) {
            return null;
        }

        String normalized = domain.trim().replaceAll("/+$", "");

        if (!normalized.startsWith("https://")
                && !normalized.startsWith("http://")) {
            normalized = "https://" + normalized;
        }

        return normalized;
    }

    @Override
    public void deleteImage(String storageKey) {

        if (storageKey == null || storageKey.isBlank()) {
            return;
        }

        try {

            DeleteObjectRequest deleteRequest =
                    DeleteObjectRequest.builder()
                            .bucket(bucketName)
                            .key(storageKey)
                            .build();

            s3Client.deleteObject(deleteRequest);

            log.info(
                    "Successfully deleted image from S3. Bucket={}, Key={}",
                    bucketName,
                    storageKey
            );

        } catch (S3Exception e) {

            log.error(
                    "Failed to delete image. Key={}, AWS error={}",
                    storageKey,
                    e.awsErrorDetails() != null
                            ? e.awsErrorDetails().errorCode()
                            : "UNKNOWN",
                    e
            );

            // Keep deletion non-blocking.
        }
    }

    @Override
    public String getProviderName() {
        return "aws-s3";
    }

    private void validateFile(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new FileUploadException(
                    "File cannot be empty"
            );
        }

        if (file.getSize() > maxFileSize) {
            throw new FileUploadException(
                    "File size exceeds maximum allowed limit of "
                            + (maxFileSize / (1024 * 1024))
                            + " MB"
            );
        }

        String contentType = file.getContentType();

        if (contentType == null
                || allowedContentTypes == null
                || !allowedContentTypes.contains(
                contentType.toLowerCase(Locale.ROOT)
        )) {

            throw new FileUploadException(
                    "Invalid file type: " + contentType
                            + ". Allowed types: " + allowedContentTypes
            );
        }

        getFileExtension(contentType);
    }

    private String getFileExtension(String contentType) {

        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new FileUploadException(
                    "Unsupported image format: " + contentType
            );
        };
    }

    @PreDestroy
    public void close() {

        if (s3Client != null) {
            s3Client.close();
            log.info("AWS S3 client closed");
        }
    }
}
