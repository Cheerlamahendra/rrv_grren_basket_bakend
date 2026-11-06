package com.rrvgreenbasket.discovery.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenApiConfig
 *
 * <p><strong>What it does:</strong></p>
 * Configures the OpenAPI 3.0 (SpringDoc) metadata and Swagger UI documentation
 * for the Discovery Server's programmatic REST management endpoints.
 *
 * <p><strong>Why it is required:</strong></p>
 * Ensures enterprise API documentation standards are met from the very first service.
 * Allows developers and frontend engineers to inspect available discovery endpoints
 * directly via an interactive Swagger UI.
 *
 * <p><strong>How it works:</strong></p>
 * Declares an {@link OpenAPI} Spring bean with title, version, description, and contact info.
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8761}")
    private String serverPort;

    @Bean
    public OpenAPI discoveryServerOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("RRV Green Basket - Netflix Eureka Discovery Server API")
                        .description("Centralized Service Registry & Discovery Management API for AVM Smart Solutions' E-Commerce Microservices.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("AVM Smart Solutions Engineering Team")
                                .email("engineering@avmsmartsolutions.com")
                                .url("https://www.avmsmartsolutions.com"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://www.avmsmartsolutions.com/terms")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local Discovery Server Instance")
                ));
    }
}
