package com.rrvgreenbasket.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * ApiGatewayApplication
 *
 * <p><strong>What it does:</strong></p>
 * This is the bootstrap class for the Reactive Spring Cloud API Gateway in the RRV Green Basket
 * microservices architecture. It activates dynamic service discovery via {@link EnableDiscoveryClient}
 * and runs on an event-driven Netty reactive runtime.
 *
 * <p><strong>Why it is required:</strong></p>
 * The Next.js frontend (and external clients) must never make direct calls to 14 private microservices.
 * Direct communication causes CORS chaos, exposes internal network topologies, duplicates authentication
 * logic across every service, and prevents centralized rate limiting.
 * The API Gateway acts as the single unified entry point (Edge Router) on port 8080.
 *
 * <p><strong>How it works:</strong></p>
 * Receives all incoming HTTP requests on port 8080, handles CORS preflight, executes JWT validation,
 * checks role authorizations, injects correlation IDs for distributed tracing, and load balances
 * traffic to healthy backend instances discovered via Netflix Eureka.
 *
 * <p><strong>Interacting components:</strong></p>
 * Interacts with Next.js frontend, Netflix Eureka Discovery Server, Config Server, and all 14 domain services.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ApiGatewayApplication {

    private static final Logger log = LoggerFactory.getLogger(ApiGatewayApplication.class);

    @org.springframework.context.annotation.Bean
    public org.springframework.web.reactive.function.client.WebClient.Builder webClientBuilder() {
        return org.springframework.web.reactive.function.client.WebClient.builder();
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(ApiGatewayApplication.class);
        Environment env = app.run(args).getEnvironment();
        logApplicationStartup(env);
    }

    private static void logApplicationStartup(Environment env) {
        String protocol = "http";
        String serverPort = env.getProperty("server.port", "8080");
        String contextPath = env.getProperty("server.servlet.context-path", "/");
        String hostAddress = "localhost";
        try {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            log.warn("Unable to determine local host address, defaulting to localhost: {}", e.getMessage());
        }

        log.info("""
                --------------------------------------------------------------------------------
                \tApplication '{}' is running! Access URLs:
                \tAPI Gateway Base URL: \t\t{}://localhost:{}{}
                \tGateway Status Endpoint: \t{}://localhost:{}{}/api/gateway/status
                \tActuator Health Endpoint: \t{}://localhost:{}{}/actuator/health
                \tSwagger UI Documentation: \t{}://localhost:{}{}/swagger-ui.html
                \tActive Profiles: \t\t{}
                --------------------------------------------------------------------------------
                """,
                env.getProperty("spring.application.name", "api-gateway"),
                protocol, serverPort, contextPath,
                protocol, serverPort, contextPath.endsWith("/") ? "" : contextPath,
                protocol, serverPort, contextPath.endsWith("/") ? "" : contextPath,
                protocol, serverPort, contextPath.endsWith("/") ? "" : contextPath,
                env.getActiveProfiles().length == 0 ? "default" : env.getActiveProfiles());
    }
}
