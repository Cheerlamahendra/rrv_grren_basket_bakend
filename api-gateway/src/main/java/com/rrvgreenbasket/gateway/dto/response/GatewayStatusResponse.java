package com.rrvgreenbasket.gateway.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * GatewayStatusResponse
 *
 * <p><strong>What it does:</strong></p>
 * Operational metrics and health indicators for the API Gateway.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayStatusResponse {

    private String status;
    private long uptimeSeconds;
    private int totalRoutes;
    private Map<String, Object> memoryUsage;
}
