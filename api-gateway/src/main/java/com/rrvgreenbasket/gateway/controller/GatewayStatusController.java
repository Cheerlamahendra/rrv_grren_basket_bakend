package com.rrvgreenbasket.gateway.controller;

import com.rrvgreenbasket.gateway.dto.response.ApiResponse;
import com.rrvgreenbasket.gateway.dto.response.GatewayStatusResponse;
import com.rrvgreenbasket.gateway.dto.response.RouteDefinitionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GatewayStatusController
 *
 * <p><strong>What it does:</strong></p>
 * Reactive REST Controller exposing runtime metrics and active routing configurations
 * of the API Gateway.
 */
@RestController
@RequestMapping("/api/gateway")
@Tag(name = "API Gateway Management", description = "Endpoints for inspecting API Gateway routing state and health")
public class GatewayStatusController {

    private static final Logger log = LoggerFactory.getLogger(GatewayStatusController.class);
    private final RouteLocator routeLocator;

    public GatewayStatusController(RouteLocator routeLocator) {
        this.routeLocator = routeLocator;
    }

    @Operation(summary = "Get Gateway Health & Metrics", description = "Returns active routes count, uptime, and JVM memory utilization.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Gateway status successfully retrieved")
    })
    @GetMapping("/status")
    public Mono<ResponseEntity<ApiResponse<GatewayStatusResponse>>> getStatus() {
        log.info("REST request to fetch API Gateway status");

        long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;

        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> memoryUsage = new LinkedHashMap<>();
        memoryUsage.put("maxMemoryMB", runtime.maxMemory() / (1024 * 1024));
        memoryUsage.put("totalMemoryMB", runtime.totalMemory() / (1024 * 1024));
        memoryUsage.put("freeMemoryMB", runtime.freeMemory() / (1024 * 1024));
        memoryUsage.put("usedMemoryMB", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024));

        return routeLocator.getRoutes()
                .count()
                .map(count -> {
                    GatewayStatusResponse status = GatewayStatusResponse.builder()
                            .status("UP")
                            .uptimeSeconds(uptimeSeconds)
                            .totalRoutes(count.intValue())
                            .memoryUsage(memoryUsage)
                            .build();
                    return ResponseEntity.ok(ApiResponse.success("API Gateway is healthy and running", status));
                });
    }

    @Operation(summary = "List Active Gateway Routes", description = "Retrieves all currently configured microservice routing targets and path predicates.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Active routes successfully retrieved")
    })
    @GetMapping("/routes")
    public Mono<ResponseEntity<ApiResponse<List<RouteDefinitionResponse>>>> getRoutes() {
        log.info("REST request to list active API Gateway routes");

        return routeLocator.getRoutes()
                .map(route -> RouteDefinitionResponse.builder()
                        .routeId(route.getId())
                        .targetUri(route.getUri().toString())
                        .predicatePattern(route.getPredicate().toString())
                        .order(route.getOrder())
                        .build())
                .collectList()
                .map(routes -> ResponseEntity.ok(ApiResponse.success(
                        String.format("Retrieved %d active route(s)", routes.size()),
                        routes)));
    }
}
