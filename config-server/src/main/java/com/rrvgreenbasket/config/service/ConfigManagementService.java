package com.rrvgreenbasket.config.service;

import com.rrvgreenbasket.config.dto.response.ConfigServerStatusResponse;
import com.rrvgreenbasket.config.dto.response.HostedConfigSummaryResponse;

/**
 * ConfigManagementService
 *
 * <p><strong>What it does:</strong></p>
 * Service interface defining administrative and monitoring operations for the Config Server.
 *
 * <p><strong>Why it is required:</strong></p>
 * Decouples the Spring MVC controllers from internal Spring Cloud Config
 * {@link org.springframework.cloud.config.server.environment.EnvironmentRepository} infrastructure.
 */
public interface ConfigManagementService {

    /**
     * Retrieves overall Config Server status, active repository search locations,
     * and listed service configurations.
     *
     * @return ConfigServerStatusResponse with status metrics.
     */
    ConfigServerStatusResponse getConfigServerStatus();

    /**
     * Inspects and returns properties for a specific microservice and profile with
     * sensitive credentials safely sanitized.
     *
     * @param application Microservice application name (e.g. auth-service).
     * @param profile Active profile (e.g. dev, prod).
     * @return HostedConfigSummaryResponse.
     */
    HostedConfigSummaryResponse getHostedConfiguration(String application, String profile);
}
