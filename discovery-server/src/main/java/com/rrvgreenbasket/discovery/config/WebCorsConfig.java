package com.rrvgreenbasket.discovery.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebCorsConfig
 *
 * <p><strong>What it does:</strong></p>
 * Configures Cross-Origin Resource Sharing (CORS) mappings for the Discovery Server's
 * HTTP endpoints and Eureka dashboard.
 *
 * <p><strong>Why it is required:</strong></p>
 * The Next.js frontend (running on localhost:3000) or external administrative tools
 * need to query discovery status without triggering browser CORS security rejections.
 *
 * <p><strong>How it works:</strong></p>
 * Registers CORS mapping on {@code /**} allowing standard HTTP methods (GET, POST, OPTIONS)
 * and headers.
 */
@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
