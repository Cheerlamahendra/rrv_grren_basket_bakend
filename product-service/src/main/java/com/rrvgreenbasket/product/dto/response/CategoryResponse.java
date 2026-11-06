package com.rrvgreenbasket.product.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {

    private String publicId;
    private String name;
    private String slug;
    private String description;
    private String imageUrl;
    private Integer sortOrder;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
}
