package com.rrvgreenbasket.product.mapper;

import com.rrvgreenbasket.product.dto.response.ProductDetailResponse;
import com.rrvgreenbasket.product.dto.response.ProductImageResponse;
import com.rrvgreenbasket.product.dto.response.ProductSummaryResponse;
import com.rrvgreenbasket.product.entity.Product;
import com.rrvgreenbasket.product.entity.ProductImage;
import com.rrvgreenbasket.product.enums.StockStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ProductMapper {

    private final CategoryMapper categoryMapper;

    public ProductSummaryResponse toSummaryResponse(Product product, boolean isAvailable, StockStatus stockStatus) {
        if (product == null) {
            return null;
        }

        BigDecimal totalPrice = calculateTotalPrice(product.getPricePerKg(), product.getDefaultQuantityKg());

        List<ProductImageResponse> imageResponses = product.getImages() != null
                ? product.getImages().stream().map(this::toImageResponse).collect(Collectors.toList())
                : Collections.emptyList();

        return ProductSummaryResponse.builder()
                .publicId(product.getPublicId())
                .name(product.getName())
                .slug(product.getSlug())
                .category(categoryMapper.toResponse(product.getCategory()))
                .description(product.getDescription())
                .shortDescription(product.getShortDescription())
                .imageUrl(product.getImageUrl())
                .pricePerKg(product.getPricePerKg())
                .defaultQuantityKg(product.getDefaultQuantityKg())
                .quantityUnit(product.getQuantityUnit())
                .totalPrice(totalPrice)
                .available(isAvailable)
                .stockStatus(stockStatus)
                .isFeatured(product.getIsFeatured())
                .images(imageResponses)
                .build();
    }

    public ProductDetailResponse toDetailResponse(Product product, boolean isAvailable, StockStatus stockStatus) {
        if (product == null) {
            return null;
        }

        BigDecimal totalPrice = calculateTotalPrice(product.getPricePerKg(), product.getDefaultQuantityKg());

        List<ProductImageResponse> imageResponses = product.getImages() != null
                ? product.getImages().stream().map(this::toImageResponse).collect(Collectors.toList())
                : Collections.emptyList();

        return ProductDetailResponse.builder()
                .publicId(product.getPublicId())
                .name(product.getName())
                .slug(product.getSlug())
                .category(categoryMapper.toResponse(product.getCategory()))
                .description(product.getDescription())
                .shortDescription(product.getShortDescription())
                .pricePerKg(product.getPricePerKg())
                .defaultQuantityKg(product.getDefaultQuantityKg())
                .quantityUnit(product.getQuantityUnit())
                .totalPrice(totalPrice)
                .imageUrl(product.getImageUrl())
                .status(product.getStatus())
                .adminEnabled(product.getAdminEnabled())
                .isFeatured(product.getIsFeatured())
                .available(isAvailable)
                .stockStatus(stockStatus)
                .images(imageResponses)
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    public ProductImageResponse toImageResponse(ProductImage image) {
        if (image == null) {
            return null;
        }
        return ProductImageResponse.builder()
                .publicId(image.getPublicId())
                .storageProvider(image.getStorageProvider())
                .storageKey(image.getStorageKey())
                .imageUrl(image.getImageUrl())
                .altText(image.getAltText())
                .isPrimary(image.getIsPrimary())
                .sortOrder(image.getSortOrder())
                .createdAt(image.getCreatedAt())
                .build();
    }

    public BigDecimal calculateTotalPrice(BigDecimal pricePerKg, BigDecimal quantityKg) {
        if (pricePerKg == null || quantityKg == null) {
            return BigDecimal.ZERO;
        }
        return pricePerKg.multiply(quantityKg).setScale(2, RoundingMode.HALF_UP);
    }
}
