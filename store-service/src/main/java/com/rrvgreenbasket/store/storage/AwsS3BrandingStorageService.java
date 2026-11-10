package com.rrvgreenbasket.store.storage;

import com.rrvgreenbasket.store.enums.AssetType;
import com.rrvgreenbasket.store.exception.FileUploadException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(name = "aws.s3.provider", havingValue = "aws-s3")
public class AwsS3BrandingStorageService implements BrandingStorageService {

    private final String bucketName;
    private final String region;
    private final String cloudfrontDomain;
    private final String logoPrefix;
    private final String faviconPrefix;
    private final S3Client s3Client;

    public AwsS3BrandingStorageService(AwsS3Properties properties) {
        this.bucketName = properties.getBucketName();
        this.region = properties.getRegion();

        String cfDomain = properties.getBrandingCloudfrontDomain();
        if (cfDomain == null || cfDomain.isBlank()) {
            cfDomain = properties.getCloudfrontDomain();
        }
        this.cloudfrontDomain = normalizeDomain(cfDomain);

        this.logoPrefix = normalizePrefix(properties.getLogoPrefix(), "branding/logo/");
        this.faviconPrefix = normalizePrefix(properties.getFaviconPrefix(), "branding/favicon/");

        if (bucketName == null || bucketName.isBlank()) {
            throw new IllegalStateException("AWS S3 bucket name is not configured for branding storage");
        }
        if (region == null || region.isBlank()) {
            throw new IllegalStateException("AWS S3 region is not configured for branding storage");
        }

        this.s3Client = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        log.info("Initialized AWS S3 Branding Storage Service with Bucket: {}, Region: {}, CloudFront: {}",
                bucketName, region, cloudfrontDomain != null ? cloudfrontDomain : "NOT CONFIGURED");
    }

    @Override
    public BrandingStorageResult uploadLogo(MultipartFile file) {
        AssetValidationUtil.validateAsset(file, AssetType.LOGO);
        String extension = getExtension(file.getOriginalFilename());
        String objectKey = logoPrefix + "rrv-green-basket-logo-" + UUID.randomUUID() + extension;
        return uploadFileToS3(file, objectKey);
    }

    @Override
    public BrandingStorageResult uploadFavicon(MultipartFile file) {
        AssetValidationUtil.validateAsset(file, AssetType.FAVICON);
        String extension = getExtension(file.getOriginalFilename());
        String objectKey = faviconPrefix + "rrv-green-basket-favicon-" + UUID.randomUUID() + extension;
        return uploadFileToS3(file, objectKey);
    }

    @Override
    public void deleteAsset(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }

        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storageKey.trim())
                    .build();

            s3Client.deleteObject(deleteRequest);
            log.info("Successfully deleted branding asset from S3: Bucket={}, Key={}", bucketName, storageKey);
        } catch (S3Exception ex) {
            log.error("Failed to delete branding asset from S3: Key={}, ErrorCode={}",
                    storageKey, ex.awsErrorDetails() != null ? ex.awsErrorDetails().errorCode() : "UNKNOWN", ex);
        } catch (Exception ex) {
            log.error("Unexpected error deleting asset {}: {}", storageKey, ex.getMessage(), ex);
        }
    }

    @Override
    public String getProviderName() {
        return "aws-s3";
    }

    private BrandingStorageResult uploadFileToS3(MultipartFile file, String storageKey) {
        try (InputStream inputStream = file.getInputStream()) {
            String contentType = file.getContentType();
            if (contentType == null || contentType.isBlank()) {
                contentType = "application/octet-stream";
            }

            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storageKey)
                    .contentType(contentType)
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromInputStream(inputStream, file.getSize()));

            String deliveryUrl = buildDeliveryUrl(storageKey);
            log.info("Successfully uploaded branding asset to S3: Key={}, DeliveryUrl={}", storageKey, deliveryUrl);

            return new BrandingStorageResult(
                    storageKey,
                    deliveryUrl,
                    file.getOriginalFilename(),
                    "aws-s3"
            );

        } catch (IOException e) {
            log.error("I/O error reading branding file payload: {}", e.getMessage(), e);
            throw new FileUploadException("Failed to read uploaded image stream: " + e.getMessage(), e);
        } catch (S3Exception e) {
            String errorCode = e.awsErrorDetails() != null ? e.awsErrorDetails().errorCode() : "UNKNOWN";
            log.error("AWS S3 PutObject failed: Key={}, ErrorCode={}, Status={}", storageKey, errorCode, e.statusCode(), e);
            throw new FileUploadException(String.format("AWS S3 upload failed (%s). Check IAM permissions for prefix '%s*'",
                    errorCode, storageKey.substring(0, storageKey.lastIndexOf('/') + 1)), e);
        } catch (Exception e) {
            log.error("Unexpected AWS S3 error: {}", e.getMessage(), e);
            throw new FileUploadException("Unexpected error during AWS S3 asset upload: " + e.getMessage(), e);
        }
    }

    private String buildDeliveryUrl(String storageKey) {
        if (cloudfrontDomain != null && !cloudfrontDomain.isBlank()) {
            return cloudfrontDomain + "/" + storageKey;
        }
        // Direct private S3 URI fallback if CloudFront is not configured
        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, storageKey);
    }

    private String normalizeDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return null;
        }
        String clean = domain.trim().replaceAll("/+$", "");
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "https://" + clean;
        }
        return clean;
    }

    private String normalizePrefix(String prefix, String defaultPrefix) {
        if (prefix == null || prefix.isBlank()) {
            return defaultPrefix;
        }
        String clean = prefix.replaceAll("^/+", "");
        if (!clean.endsWith("/")) {
            clean = clean + "/";
        }
        return clean;
    }

    private String getExtension(String filename) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf(".")).toLowerCase(Locale.ROOT);
        }
        return ".png";
    }

    @PreDestroy
    public void cleanup() {
        if (s3Client != null) {
            try {
                s3Client.close();
            } catch (Exception ignored) {
            }
        }
    }
}
