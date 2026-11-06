package com.rrvgreenbasket.gateway.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RouteDefinitionResponse
 *
 * <p><strong>What it does:</strong></p>
 * Represents an active dynamic routing entry in Spring Cloud Gateway.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteDefinitionResponse {

    private String routeId;
    private String targetUri;
    private String predicatePattern;
    private int order;
}
