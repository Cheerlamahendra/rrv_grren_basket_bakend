package com.rrvgreenbasket.config.controller;

import com.rrvgreenbasket.config.dto.response.ApiResponse;
import com.rrvgreenbasket.config.dto.response.ConfigServerStatusResponse;
import com.rrvgreenbasket.config.dto.response.HostedConfigSummaryResponse;
import com.rrvgreenbasket.config.service.ConfigManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * ConfigManagementController
 *
 * <p><strong>What it does:</strong></p>
 * REST Controller exposing administrative and monitoring APIs for the Centralized Config Server.
 *
 * <p><strong>Why it is required:</strong></p>
 * In addition to Spring Cloud Config's raw property endpoints, this controller provides
 * structured JSON responses wrapped in the standard {@link ApiResponse} envelope for the Next.js
 * administrative portal and DevOps dashboards.
 */
@RestController
@RequestMapping("/api/config")
@Tag(name = "Config Server Management", description = "Endpoints for inspecting centralized configurations and server metrics")
public class ConfigManagementController {

    private static final Logger log = LoggerFactory.getLogger(ConfigManagementController.class);

    private final ConfigManagementService configManagementService;

    public ConfigManagementController(ConfigManagementService configManagementService) {
        this.configManagementService = configManagementService;
    }

    @Operation(summary = "Get Config Server Operational Status", description = "Returns active profiles, repository search locations, available service templates, and memory statistics.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Config Server status successfully retrieved")
    })
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<ConfigServerStatusResponse>> getStatus() {
        log.info("REST request to fetch Config Server status");
        ConfigServerStatusResponse status = configManagementService.getConfigServerStatus();
        return ResponseEntity.ok(ApiResponse.success("Config Server is healthy and running", status));
    }

    @Operation(summary = "Inspect Microservice Configuration", description = "Inspects composite configuration properties for a specific service and profile with credentials masked.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Configuration successfully retrieved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Configuration file not found for the requested application/profile")
    })
    @GetMapping("/services/{application}/{profile}")
    public ResponseEntity<ApiResponse<HostedConfigSummaryResponse>> getServiceConfiguration(
            @Parameter(description = "Application name (e.g. auth-service, product-service)", example = "auth-service")
            @PathVariable String application,
            @Parameter(description = "Target profile (e.g. dev, prod)", example = "dev")
            @PathVariable String profile) {
        log.info("REST request to inspect configuration for application='{}', profile='{}'", application, profile);
        HostedConfigSummaryResponse config = configManagementService.getHostedConfiguration(application, profile);
        return ResponseEntity.ok(ApiResponse.success(
                String.format("Loaded configuration for '%s' [%s]", application, profile),
                config));
    }
}
