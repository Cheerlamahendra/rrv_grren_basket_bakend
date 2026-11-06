package com.rrvgreenbasket.auth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenApiConfig
 *
 * <p><strong>What it does:</strong></p>
 * Configures OpenAPI 3.0 / Swagger documentation with Bearer JWT security scheme.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI authServiceOpenAPI() {
        final String securitySchemeName = "BearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("RRV Green Basket — Auth Service API")
                        .description("Authentication microservice for RRV Green Basket fresh produce platform. " +
                                "Supports Customer passwordless email OTP verification, Admin BCrypt login with lockout protection, " +
                                "and HMAC-SHA signed JWT access and refresh token lifecycle.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("AVM Smart Solutions — Engineering Team")
                                .email("engineering@avmsmart.com")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                                .name(securitySchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Enter JWT Bearer token obtained from login or registration.")));
    }
}
