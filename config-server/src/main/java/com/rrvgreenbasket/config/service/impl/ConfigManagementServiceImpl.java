package com.rrvgreenbasket.config.service.impl;

import com.rrvgreenbasket.config.dto.response.ConfigServerStatusResponse;
import com.rrvgreenbasket.config.dto.response.HostedConfigSummaryResponse;
import com.rrvgreenbasket.config.exception.ConfigurationNotFoundException;
import com.rrvgreenbasket.config.service.ConfigManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ConfigManagementServiceImpl
 *
 * <p><strong>What it does:</strong></p>
 * Service implementation that queries Spring Cloud Config's {@link EnvironmentRepository}
 * to return centralized configuration summaries, with automated sensitive-data masking.
 *
 * <p><strong>Why it is required:</strong></p>
 * Allows developers and monitoring consoles to verify what configurations a microservice
 * will receive at runtime without exposing real passwords, JWT secrets, or cloud keys.
 *
 * <p><strong>How it works:</strong></p>
 * Uses {@link EnvironmentRepository#findOne(String, String, String, boolean)} to load
 * composite property sources, masks sensitive keys (passwords, secrets, credentials),
 * and maps the result into strongly-typed DTOs.
 */
@Service
public class ConfigManagementServiceImpl implements ConfigManagementService {

    private static final Logger log = LoggerFactory.getLogger(ConfigManagementServiceImpl.class);

    private static final Set<String> SENSITIVE_KEYWORDS = Set.of(
            "password", "secret", "key", "token", "credential", "private", "access-key"
    );

    private final EnvironmentRepository environmentRepository;

    @Value("${spring.profiles.active:native}")
    private String activeProfile;

    @Value("${spring.cloud.config.server.native.search-locations:classpath:/config-repo,file:./config-repo}")
    private List<String> searchLocations;

    public ConfigManagementServiceImpl(EnvironmentRepository environmentRepository) {
        this.environmentRepository = environmentRepository;
    }

    @Override
    public ConfigServerStatusResponse getConfigServerStatus() {
        log.debug("Fetching Config Server operational metrics");

        long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;

        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> memoryUsage = new LinkedHashMap<>();
        memoryUsage.put("maxMemoryMB", runtime.maxMemory() / (1024 * 1024));
        memoryUsage.put("totalMemoryMB", runtime.totalMemory() / (1024 * 1024));
        memoryUsage.put("freeMemoryMB", runtime.freeMemory() / (1024 * 1024));
        memoryUsage.put("usedMemoryMB", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024));

        List<String> knownServices = List.of(
                "application", "api-gateway", "auth-service", "customer-service",
                "admin-service", "product-service", "inventory-service", "cart-service",
                "promotion-service", "order-service", "procurement-service",
                "packing-service", "delivery-service", "payment-service",
                "notification-service", "analytics-service"
        );

        return ConfigServerStatusResponse.builder()
                .status("UP")
                .activeProfile(activeProfile)
                .uptimeSeconds(uptimeSeconds)
                .searchLocations(searchLocations)
                .availableServices(knownServices)
                .memoryUsage(memoryUsage)
                .build();
    }

    @Override
    public HostedConfigSummaryResponse getHostedConfiguration(String application, String profile) {
        log.debug("Resolving hosted configuration for application='{}', profile='{}'", application, profile);

        Environment env = environmentRepository.findOne(application, profile, "main", false);
        if (env == null || env.getPropertySources().isEmpty()) {
            throw new ConfigurationNotFoundException(application, profile);
        }

        List<String> propertySourceNames = env.getPropertySources().stream()
                .map(PropertySource::getName)
                .collect(Collectors.toList());

        Map<String, Object> sanitizedProps = new LinkedHashMap<>();
        for (PropertySource ps : env.getPropertySources()) {
            Map<?, ?> sourceMap = ps.getSource();
            if (sourceMap != null) {
                for (Map.Entry<?, ?> entry : sourceMap.entrySet()) {
                    String key = String.valueOf(entry.getKey());
                    Object val = entry.getValue();
                    if (isSensitiveKey(key)) {
                        sanitizedProps.put(key, "******");
                    } else {
                        sanitizedProps.put(key, val);
                    }
                }
            }
        }

        return HostedConfigSummaryResponse.builder()
                .application(env.getName())
                .profile(String.join(",", env.getProfiles()))
                .label(env.getLabel())
                .version(env.getVersion())
                .propertySources(propertySourceNames)
                .sanitizedProperties(sanitizedProps)
                .build();
    }

    private boolean isSensitiveKey(String key) {
        String lower = key.toLowerCase();
        return SENSITIVE_KEYWORDS.stream().anyMatch(lower::contains);
    }
}
