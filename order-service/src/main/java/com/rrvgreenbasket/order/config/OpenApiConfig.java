package com.rrvgreenbasket.order.config;

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
                        .title("RRV Green Basket - Order Service API")
                        .version("1.0.0")
                        .description("Microservice for Order Placement, Next-Day Delivery Scheduling, Timing Cutoffs, Inventory Reservation & Confirmation, Pricing Calculation, and Order Lifecycle Management")
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
