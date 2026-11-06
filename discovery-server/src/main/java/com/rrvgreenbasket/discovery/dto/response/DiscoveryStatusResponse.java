package com.rrvgreenbasket.discovery.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DiscoveryStatusResponse
 *
 * <p><strong>What it does:</strong></p>
 * Provides operational metrics and health indicators of the Eureka Service Registry.
 *
 * <p><strong>Why it is required:</strong></p>
 * Allows administrators and monitoring dashboards (or Prometheus/Grafana) to quickly verify
 * how many services are registered and if the cluster is in self-preservation mode.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscoveryStatusResponse {

    private String environment;
    private String status;
    private long uptimeSeconds;
    private int totalRegisteredApplications;
    private int totalActiveInstances;
    private boolean selfPreservationModeEnabled;
    private double renewalPercentThreshold;
    private Map<String, Object> memoryUsage;
}
