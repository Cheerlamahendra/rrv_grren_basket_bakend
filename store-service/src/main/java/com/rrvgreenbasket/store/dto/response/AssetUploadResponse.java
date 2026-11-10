package com.rrvgreenbasket.store.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rrvgreenbasket.store.enums.AssetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AssetUploadResponse {

    private AssetType assetType;
    private String fileName;
    private String logoUrl;
    private String faviconUrl;
    private String assetUrl;
    @Builder.Default
    private Boolean uploaded = true;

    public static AssetUploadResponse forLogo(String fileName, String logoUrl) {
        return AssetUploadResponse.builder()
                .assetType(AssetType.LOGO)
                .fileName(fileName)
                .logoUrl(logoUrl)
                .assetUrl(logoUrl)
                .uploaded(true)
                .build();
    }

    public static AssetUploadResponse forFavicon(String fileName, String faviconUrl) {
        return AssetUploadResponse.builder()
                .assetType(AssetType.FAVICON)
                .fileName(fileName)
                .faviconUrl(faviconUrl)
                .assetUrl(faviconUrl)
                .uploaded(true)
                .build();
    }
}
