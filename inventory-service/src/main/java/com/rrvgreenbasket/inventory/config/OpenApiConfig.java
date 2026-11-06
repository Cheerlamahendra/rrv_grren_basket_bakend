package com.rrvgreenbasket.inventory.config;

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

    @Value("${server.port:8085}")
    private String serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("RRV Green Basket — Inventory Microservice API")
                        .version("1.0.0")
                        .description("Centralized single-warehouse stock ledger, reservations, stock-in/out, adjustments, alerts, and outbox messaging.")
                        .contact(new Contact()
                                .name("RRV Green Basket Engineering")
                                .email("dev@rrvgreenbasket.com"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://rrvgreenbasket.com")))
                .servers(List.of(
                        new Server().url("http://localhost:" + serverPort).description("Direct Inventory Service"),
                        new Server().url("http://localhost:8080").description("API Gateway")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .name("bearerAuth")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Enter standard JWT bearer token for Admin API access")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
