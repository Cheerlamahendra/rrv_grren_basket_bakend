package com.rrvgreenbasket.delivery.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("RRV Green Basket - Delivery Service API")
                        .version("2.0.0")
                        .description("Microservice for Google Maps Integration, Address Verification (Places API New), Store Geofencing (5 km), Actual Road Distance Routing (Routes API), Dynamic Delivery Pricing Slabs, and Checkout Availability Validation.")
                        .contact(new Contact().name("RRV Green Basket Engineering").email("dev@rrvgreenbasket.com")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter verified X-Gateway-Assertion or JWT Bearer Token")));
    }
}
