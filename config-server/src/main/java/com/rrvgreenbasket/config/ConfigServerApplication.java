package com.rrvgreenbasket.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.config.server.EnableConfigServer;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * ConfigServerApplication
 *
 * <p><strong>What it does:</strong></p>
 * This is the bootstrap class for the Centralized Spring Cloud Config Server in the
 * RRV Green Basket microservices architecture. It activates config server features via
 * {@link EnableConfigServer} and registers itself with Eureka via {@link EnableDiscoveryClient}.
 *
 * <p><strong>Why it is required:</strong></p>
 * Across 17 microservices, managing individual configuration files (database credentials,
 * JWT signing keys, AWS S3 buckets, email API endpoints, store cutoffs) would create configuration
 * drift, deployment bottlenecks, and security vulnerabilities. Config Server provides a single
 * source of truth that externalizes configurations per environment (dev, test, prod).
 *
 * <p><strong>How it works:</strong></p>
 * Listens on port 8888. When downstream microservices (e.g. auth-service, product-service) start,
 * they query this server (e.g. `GET /auth-service/dev`) to receive their environment properties.
 *
 * <p><strong>Interacting components:</strong></p>
 * Interacts with Eureka Discovery Server (registers as `CONFIG-SERVER`) and serves configuration
 * to every business microservice.
 */
@SpringBootApplication
@EnableConfigServer
@EnableDiscoveryClient
public class ConfigServerApplication {

    private static final Logger log = LoggerFactory.getLogger(ConfigServerApplication.class);

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(ConfigServerApplication.class);
        Environment env = app.run(args).getEnvironment();
        logApplicationStartup(env);
    }

    private static void logApplicationStartup(Environment env) {
        String protocol = "http";
        String serverPort = env.getProperty("server.port", "8888");
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
                \tConfig Server Base URL: \t{}://localhost:{}{}
                \tSample Config Query: \t\t{}://localhost:{}{}/auth-service/dev
                \tActuator Health Endpoint: \t{}://localhost:{}{}/actuator/health
                \tSwagger UI Documentation: \t{}://localhost:{}{}/swagger-ui.html
                \tActive Profiles: \t\t{}
                --------------------------------------------------------------------------------
                """,
                env.getProperty("spring.application.name", "config-server"),
                protocol, serverPort, contextPath,
                protocol, serverPort, contextPath.endsWith("/") ? "" : contextPath,
                protocol, serverPort, contextPath.endsWith("/") ? "" : contextPath,
                protocol, serverPort, contextPath.endsWith("/") ? "" : contextPath,
                env.getActiveProfiles().length == 0 ? "native (default)" : env.getActiveProfiles());
    }
}
