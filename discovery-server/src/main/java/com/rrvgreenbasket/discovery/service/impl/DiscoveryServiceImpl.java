package com.rrvgreenbasket.discovery.service.impl;

import com.rrvgreenbasket.discovery.dto.response.DiscoveryStatusResponse;
import com.rrvgreenbasket.discovery.dto.response.RegisteredServiceResponse;
import com.rrvgreenbasket.discovery.dto.response.ServiceInstanceResponse;
import com.rrvgreenbasket.discovery.exception.ResourceNotFoundException;
import com.rrvgreenbasket.discovery.service.DiscoveryService;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Application;
import com.netflix.eureka.EurekaServerContext;
import com.netflix.eureka.EurekaServerContextHolder;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.stream.Collectors;

/**
 * DiscoveryServiceImpl
 *
 * <p><strong>What it does:</strong></p>
 * Implementation of {@link DiscoveryService} that programmatically inspects the in-memory
 * Eureka {@link PeerAwareInstanceRegistry} to extract registered applications, node instances,
 * health statuses, and runtime statistics.
 *
 * <p><strong>Why it is required:</strong></p>
 * While Eureka provides an HTML web dashboard, enterprise architectures require programmatic
 * JSON REST APIs for health monitoring, automated DevOps smoke-testing, and client dashboards.
 *
 * <p><strong>How it works:</strong></p>
 * Accesses {@link EurekaServerContextHolder#getInstance()} to retrieve the current
 * {@link EurekaServerContext}. It maps internal Netflix {@link Application} and {@link InstanceInfo}
 * objects into immutable, clean DTOs.
 */
@Service
public class DiscoveryServiceImpl implements DiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryServiceImpl.class);

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @Value("${eureka.server.enable-self-preservation:false}")
    private boolean selfPreservationConfigured;

    @Value("${eureka.server.renewal-percent-threshold:0.85}")
    private double renewalPercentThreshold;

    private PeerAwareInstanceRegistry getRegistry() {
        EurekaServerContext serverContext = EurekaServerContextHolder.getInstance().getServerContext();
        if (serverContext != null) {
            return serverContext.getRegistry();
        }
        return null;
    }

    @Override
    public DiscoveryStatusResponse getDiscoveryStatus() {
        log.debug("Fetching Eureka registry status and metrics");
        PeerAwareInstanceRegistry registry = getRegistry();

        int totalApplications = 0;
        int totalInstances = 0;

        if (registry != null) {
            List<Application> apps = registry.getSortedApplications();
            totalApplications = apps.size();
            totalInstances = apps.stream().mapToInt(Application::size).sum();
        }

        long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;

        // JVM Memory Statistics
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> memoryUsage = new LinkedHashMap<>();
        memoryUsage.put("maxMemoryMB", runtime.maxMemory() / (1024 * 1024));
        memoryUsage.put("totalMemoryMB", runtime.totalMemory() / (1024 * 1024));
        memoryUsage.put("freeMemoryMB", runtime.freeMemory() / (1024 * 1024));
        memoryUsage.put("usedMemoryMB", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024));

        return DiscoveryStatusResponse.builder()
                .environment(activeProfile)
                .status("UP")
                .uptimeSeconds(uptimeSeconds)
                .totalRegisteredApplications(totalApplications)
                .totalActiveInstances(totalInstances)
                .selfPreservationModeEnabled(selfPreservationConfigured)
                .renewalPercentThreshold(renewalPercentThreshold)
                .memoryUsage(memoryUsage)
                .build();
    }

    @Override
    public List<RegisteredServiceResponse> getAllRegisteredServices() {
        log.debug("Fetching all registered services from Eureka registry");
        PeerAwareInstanceRegistry registry = getRegistry();
        if (registry == null) {
            return Collections.emptyList();
        }

        return registry.getSortedApplications().stream()
                .map(this::mapApplicationToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public RegisteredServiceResponse getServiceByName(String serviceName) {
        log.debug("Fetching service instances for serviceName='{}'", serviceName);
        PeerAwareInstanceRegistry registry = getRegistry();
        if (registry == null) {
            throw new ResourceNotFoundException("Eureka Registry", "serviceName", serviceName);
        }

        Application application = registry.getApplication(serviceName.toUpperCase());
        if (application == null) {
            throw new ResourceNotFoundException("Registered Service", "name", serviceName);
        }

        return mapApplicationToResponse(application);
    }

    private RegisteredServiceResponse mapApplicationToResponse(Application app) {
        List<ServiceInstanceResponse> instances = app.getInstances().stream()
                .map(this::mapInstanceInfoToResponse)
                .collect(Collectors.toList());

        return RegisteredServiceResponse.builder()
                .serviceName(app.getName())
                .instanceCount(instances.size())
                .instances(instances)
                .build();
    }

    private ServiceInstanceResponse mapInstanceInfoToResponse(InstanceInfo info) {
        return ServiceInstanceResponse.builder()
                .serviceName(info.getAppName())
                .instanceId(info.getId())
                .hostName(info.getHostName())
                .ipAddress(info.getIPAddr())
                .port(info.getPort())
                .status(info.getStatus() != null ? info.getStatus().name() : "UNKNOWN")
                .healthCheckUrl(info.getHealthCheckUrl())
                .homePageUrl(info.getHomePageUrl())
                .leaseRenewalIntervalSeconds(info.getLeaseInfo() != null ? info.getLeaseInfo().getRenewalIntervalInSecs() : 30)
                .leaseDurationSeconds(info.getLeaseInfo() != null ? info.getLeaseInfo().getDurationInSecs() : 90)
                .metadata(info.getMetadata() != null ? new HashMap<>(info.getMetadata()) : Collections.emptyMap())
                .build();
    }
}
