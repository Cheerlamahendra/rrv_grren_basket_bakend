package com.rrvgreenbasket.store.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminStoreSettingsResponse {

    private String publicId;
    private String storeName;
    private String brandTagline;
    private String supportEmail;
    private String supportPhone;
    private String physicalAddress;
    private String websiteUrl;
    private String logoFileName;
    private String logoS3Key;
    private String logoUrl;
    private String faviconFileName;
    private String faviconS3Key;
    private String faviconUrl;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
    private String updatedBy;
}
