package com.rrvgreenbasket.review.config;

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

    @Value("${server.port:8088}")
    private String serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("RRV Green Basket - Product Review & Ratings Service API")
                        .description("Microservice managing customer reviews, product ratings, bulk card rating summaries, and administrator moderation")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("RRV Green Basket Architecture Team")
                                .email("dev@rrvgreenbasket.in"))
                        .license(new License().name("Proprietary").url("https://www.rrvgreenbasket.in")))
                .servers(List.of(
                        new Server().url("/").description("Default Gateway Relative Server"),
                        new Server().url("http://localhost:" + serverPort).description("Direct Local Review Service")
                ))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter your JWT Bearer token")));
    }
}
