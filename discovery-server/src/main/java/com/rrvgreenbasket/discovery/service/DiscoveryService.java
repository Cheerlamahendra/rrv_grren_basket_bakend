package com.rrvgreenbasket.discovery.service;

import com.rrvgreenbasket.discovery.dto.response.DiscoveryStatusResponse;
import com.rrvgreenbasket.discovery.dto.response.RegisteredServiceResponse;
import com.rrvgreenbasket.discovery.dto.response.ServiceInstanceResponse;

import java.util.List;

/**
 * DiscoveryService
 *
 * <p><strong>What it does:</strong></p>
 * Service interface declaring business operations for querying and monitoring the
 * Eureka Service Registry.
 *
 * <p><strong>Why it is required:</strong></p>
 * Decouples the REST Controller layer from direct Netflix Eureka internal SDK classes,
 * adhering to clean architecture principles.
 */
public interface DiscoveryService {

    /**
     * Retrieves overall Eureka registry metrics, uptime, and cluster status.
     *
     * @return DiscoveryStatusResponse with registry metrics.
     */
    DiscoveryStatusResponse getDiscoveryStatus();

    /**
     * Lists all registered microservice applications and their active nodes.
     *
     * @return List of RegisteredServiceResponse.
     */
    List<RegisteredServiceResponse> getAllRegisteredServices();

    /**
     * Retrieves instances belonging to a specific named service (e.g., AUTH-SERVICE).
     *
     * @param serviceName Name of the microservice.
     * @return RegisteredServiceResponse containing instances.
     */
    RegisteredServiceResponse getServiceByName(String serviceName);
}
