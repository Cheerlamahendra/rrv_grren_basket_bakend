package com.rrvgreenbasket.gateway.config;

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
 * Configures OpenAPI 3.0 specification for the Reactive API Gateway.
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI apiGatewayOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("RRV Green Basket - Reactive API Gateway API")
                        .description("Edge Routing, JWT Authentication, and Central Entrypoint API for AVM Smart Solutions' E-Commerce Microservices.")
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
                                .description("API Gateway Entry Server")
                ));
    }
}
