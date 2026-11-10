package com.rrvgreenbasket.store.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "aws.s3")
public class AwsS3Properties {

    private String provider = "aws-s3";
    private String region = "ap-southeast-2";
    private String bucketName = "vegetable-ecommerce-product-images-2026";
    private String cloudfrontDomain;
    private String brandingCloudfrontDomain;
    private String logoPrefix = "branding/logo/";
    private String faviconPrefix = "branding/favicon/";
    private long maxFileSizeBytes = 2097152; // 2 MB
}
