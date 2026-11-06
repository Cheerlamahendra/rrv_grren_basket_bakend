package com.rrvgreenbasket.discovery.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * ServiceInstanceResponse
 *
 * <p><strong>What it does:</strong></p>
 * Represents metadata and connection details of a registered microservice node in Eureka.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceInstanceResponse {

    private String serviceName;
    private String instanceId;
    private String hostName;
    private String ipAddress;
    private int port;
    private String status;
    private String healthCheckUrl;
    private String homePageUrl;
    private int leaseRenewalIntervalSeconds;
    private int leaseDurationSeconds;
    private Map<String, String> metadata;
}
