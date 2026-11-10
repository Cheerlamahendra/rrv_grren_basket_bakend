package com.rrvgreenbasket.store.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicStoreSettingsResponse {

    private String storeName;
    private String brandTagline;
    private String supportEmail;
    private String supportPhone;
    private String physicalAddress;
    private String websiteUrl;
    private String logoUrl;
    private String faviconUrl;
}
