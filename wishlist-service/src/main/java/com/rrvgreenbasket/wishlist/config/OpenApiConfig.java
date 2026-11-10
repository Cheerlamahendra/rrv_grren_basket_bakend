package com.rrvgreenbasket.wishlist.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8087}")
    private String serverPort;

    @Bean
    public OpenAPI wishlistServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("RRV Green Basket — Wishlist Service API")
                        .description("Production-Grade Wishlist Microservice for RRV Green Basket platform. " +
                                "Allows authenticated customers to save products, inspect real-time product details, " +
                                "remove items, clear wishlists, and query wishlist item counts and product statuses.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("RRV Green Basket Engineering")
                                .email("engineering@rrvgreenbasket.com"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://rrvgreenbasket.com")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("API Gateway (Edge)"),
                        new Server().url("http://localhost:" + serverPort).description("Direct Wishlist Service Local")
                ))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication", new SecurityScheme()
                                .name("Bearer Authentication")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Enter standard JWT bearer token derived from Auth Service.")));
    }
}
