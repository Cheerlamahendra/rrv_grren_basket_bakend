package com.rrvgreenbasket.product.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductImageResponse {

    private String publicId;
    private String storageProvider;
    private String storageKey;
    private String imageUrl;
    private String altText;
    private Boolean isPrimary;
    private Integer sortOrder;
    private Instant createdAt;

    @JsonProperty("primary")
    public Boolean getPrimary() {
        return isPrimary;
    }
}
