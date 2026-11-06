package com.rrvgreenbasket.discovery.controller;

import com.rrvgreenbasket.discovery.dto.response.ApiResponse;
import com.rrvgreenbasket.discovery.dto.response.DiscoveryStatusResponse;
import com.rrvgreenbasket.discovery.dto.response.RegisteredServiceResponse;
import com.rrvgreenbasket.discovery.service.DiscoveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * DiscoveryController
 *
 * <p><strong>What it does:</strong></p>
 * REST Controller providing programmatic access to the Eureka Service Registry state.
 *
 * <p><strong>Why it is required:</strong></p>
 * Enables monitoring tools, automated CI/CD smoke tests, and the Next.js admin portal
 * to inspect which microservices are online, healthy, and discovering each other.
 *
 * <p><strong>How it works:</strong></p>
 * Delegates business calls to {@link DiscoveryService} and wraps all output in standard
 * {@link ApiResponse} envelopes.
 */
@RestController
@RequestMapping("/api/discovery")
@Tag(name = "Discovery Server Management", description = "Endpoints for inspecting Eureka Service Registry status and registered microservices")
public class DiscoveryController {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryController.class);

    private final DiscoveryService discoveryService;

    public DiscoveryController(DiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    @Operation(summary = "Get Eureka Registry Health & Metrics", description = "Returns active registered services count, active instances, uptime, and JVM memory utilization.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Registry status successfully retrieved")
    })
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<DiscoveryStatusResponse>> getDiscoveryStatus() {
        log.info("REST request to fetch Eureka discovery status");
        DiscoveryStatusResponse status = discoveryService.getDiscoveryStatus();
        return ResponseEntity.ok(ApiResponse.success("Eureka Discovery Server is healthy and running", status));
    }

    @Operation(summary = "List All Registered Services", description = "Retrieves all currently registered microservices and their individual running node instances.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List of registered services retrieved successfully")
    })
    @GetMapping("/services")
    public ResponseEntity<ApiResponse<List<RegisteredServiceResponse>>> getAllRegisteredServices() {
        log.info("REST request to list all registered microservices");
        List<RegisteredServiceResponse> services = discoveryService.getAllRegisteredServices();
        return ResponseEntity.ok(ApiResponse.success(
                String.format("Retrieved %d registered service(s)", services.size()),
                services));
    }

    @Operation(summary = "Get Service Instances by Name", description = "Retrieves all active instances, IP addresses, ports, and health URLs for a specific microservice name (e.g. AUTH-SERVICE).")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Service details retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Service name not found in registry")
    })
    @GetMapping("/services/{serviceName}")
    public ResponseEntity<ApiResponse<RegisteredServiceResponse>> getServiceByName(
            @Parameter(description = "Logical name of the microservice (case-insensitive)", example = "AUTH-SERVICE")
            @PathVariable String serviceName) {
        log.info("REST request to fetch details for serviceName='{}'", serviceName);
        RegisteredServiceResponse service = discoveryService.getServiceByName(serviceName);
        return ResponseEntity.ok(ApiResponse.success(
                String.format("Found service '%s' with %d active instance(s)", serviceName, service.getInstanceCount()),
                service));
    }
}
