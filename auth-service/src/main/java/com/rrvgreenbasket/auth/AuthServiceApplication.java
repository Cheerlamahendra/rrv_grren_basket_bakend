package com.rrvgreenbasket.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * AuthServiceApplication
 *
 * <p><strong>What it does:</strong></p>
 * Entry point for the Auth Service in the RRV Green Basket E-Commerce platform.
 *
 * <p><strong>Core Responsibilities:</strong></p>
 * <ul>
 *   <li>Customer passwordless authentication using 6-digit email OTPs.</li>
 *   <li>Administrative authentication with BCrypt hashing and account lockout throttling.</li>
 *   <li>HMAC-SHA256 JWT access and refresh token minting, rotation, and revocation.</li>
 *   <li>Hexagonal abstraction for external Email API provider integration.</li>
 * </ul>
 */
@SpringBootApplication
@EnableDiscoveryClient
public class AuthServiceApplication {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceApplication.class);

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(AuthServiceApplication.class);
        Environment env = app.run(args).getEnvironment();
        logApplicationStartup(env);
    }

    private static void logApplicationStartup(Environment env) {
        String protocol = "http";
        if (env.getProperty("server.ssl.key-store") != null) {
            protocol = "https";
        }
        String serverPort = env.getProperty("server.port", "8081");
        String contextPath = env.getProperty("server.servlet.context-path", "/");
        String hostAddress = "localhost";
        try {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            log.warn("The host name could not be determined, using `localhost` as fallback");
        }

        log.info("""
                
                ----------------------------------------------------------
                \tApplication '{}' is running! Access URLs:
                \tLocal: \t\t{}://localhost:{}{}
                \tExternal: \t{}://{}:{}{}
                \tSwagger UI: \t{}://localhost:{}{}swagger-ui.html
                \tOpenAPI Docs: \t{}://localhost:{}{}v3/api-docs
                \tActuator: \t{}://localhost:{}{}actuator/health
                \tProfile(s): \t{}
                ----------------------------------------------------------""",
                env.getProperty("spring.application.name", "auth-service"),
                protocol, serverPort, contextPath,
                protocol, hostAddress, serverPort, contextPath,
                protocol, serverPort, contextPath,
                protocol, serverPort, contextPath,
                protocol, serverPort, contextPath,
                env.getActiveProfiles().length == 0 ? new String[]{"default"} : env.getActiveProfiles()
        );
    }
}
