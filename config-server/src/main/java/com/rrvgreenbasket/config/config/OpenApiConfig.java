package com.rrvgreenbasket.config.config;

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
 * Configures the OpenAPI 3.0 specification and Swagger UI for the Config Server.
 *
 * <p><strong>Why it is required:</strong></p>
 * Ensures developers and devops engineers can visually inspect and test config retrieval
 * endpoints and health metrics via Swagger UI.
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8888}")
    private String serverPort;

    @Bean
    public OpenAPI configServerOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("RRV Green Basket - Spring Cloud Config Server API")
                        .description("Centralized Configuration Management Service for AVM Smart Solutions' E-Commerce Microservices.")
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
                                .description("Local Config Server Instance")
                ));
    }
}
