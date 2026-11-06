package com.rrvgreenbasket.config.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * ConfigServerStatusResponse
 *
 * <p><strong>What it does:</strong></p>
 * Exposes the operational status of the Config Server, registered search locations,
 * active profiles, and list of hosted microservice configuration templates.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigServerStatusResponse {

    private String status;
    private String activeProfile;
    private long uptimeSeconds;
    private List<String> searchLocations;
    private List<String> availableServices;
    private Map<String, Object> memoryUsage;
}
