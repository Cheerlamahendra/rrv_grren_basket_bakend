package com.rrvgreenbasket.discovery.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RegisteredServiceResponse
 *
 * <p><strong>What it does:</strong></p>
 * Groups instances by logical microservice name (e.g. AUTH-SERVICE with 2 running instances).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisteredServiceResponse {

    private String serviceName;
    private int instanceCount;
    private List<ServiceInstanceResponse> instances;
}
