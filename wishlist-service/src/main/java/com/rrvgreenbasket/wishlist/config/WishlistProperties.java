package com.rrvgreenbasket.wishlist.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "wishlist")
public class WishlistProperties {

    private Validation validation = new Validation();
    private ProductService productService = new ProductService();

    @Data
    public static class Validation {
        private int maxBulkSize = 50;
    }

    @Data
    public static class ProductService {
        private String url = "http://product-service";
        private int timeoutMs = 3000;
        private boolean mockFallbackEnabled = true;
    }
}
