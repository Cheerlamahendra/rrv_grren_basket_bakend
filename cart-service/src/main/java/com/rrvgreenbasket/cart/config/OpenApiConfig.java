package com.rrvgreenbasket.cart.config;

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

    @Value("${server.port:8086}")
    private String serverPort;

    @Bean
    public OpenAPI cartServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("RRV Green Basket — Cart Service API")
                        .description("Production-Grade Shopping Cart Microservice for RRV Green Basket platform. " +
                                "Provides customer-specific active shopping carts, live product price refresh, " +
                                "stock validation, pre-checkout cart validation, and automated cart expiration.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("RRV Green Basket Engineering")
                                .email("engineering@rrvgreenbasket.com"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://rrvgreenbasket.com")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("API Gateway (Edge)"),
                        new Server().url("http://localhost:" + serverPort).description("Direct Cart Service Local")
                ))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication", new SecurityScheme()
                                .name("Bearer Authentication")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Enter standard JWT bearer token derived from Auth Service or X-Gateway-Assertion header.")));
    }
}
