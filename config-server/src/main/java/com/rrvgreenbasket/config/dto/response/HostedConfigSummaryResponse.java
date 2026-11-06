package com.rrvgreenbasket.config.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * HostedConfigSummaryResponse
 *
 * <p><strong>What it does:</strong></p>
 * Represents the configuration snapshot returned when inspecting properties for
 * a target microservice and profile.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HostedConfigSummaryResponse {

    private String application;
    private String profile;
    private String label;
    private String version;
    private List<String> propertySources;
    private Map<String, Object> sanitizedProperties;
}
