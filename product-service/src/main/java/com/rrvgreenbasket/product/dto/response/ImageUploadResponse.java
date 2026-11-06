package com.rrvgreenbasket.product.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageUploadResponse {

    private String publicId;
    private String productPublicId;
    private String storageProvider;
    private String storageKey;
    private String imageUrl;
    private String altText;
    private Boolean isPrimary;
    private Integer sortOrder;
}
