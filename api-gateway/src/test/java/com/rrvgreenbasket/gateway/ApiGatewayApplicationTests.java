package com.rrvgreenbasket.gateway;

import com.rrvgreenbasket.gateway.dto.response.ApiResponse;
import com.rrvgreenbasket.gateway.dto.response.GatewayStatusResponse;
import com.rrvgreenbasket.gateway.filter.RequestTracingFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ApiGatewayApplicationTests
 *
 * <p><strong>What it does:</strong></p>
 * Integration tests verifying Reactive Spring Cloud Gateway route initialization,
 * correlation ID tracing, and JWT edge security enforcement.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGatewayApplicationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("Verify Spring Reactive Context Loads with Gateway Engine")
    void contextLoads() {
        // Confirms reactive pipeline initializes without Netty/Servlet conflicts
    }

    @Test
    @DisplayName("Verify Gateway Status Endpoint returns HTTP 200 with Total Routes Count")
    void shouldReturnGatewayStatusSuccessfully() {
        webTestClient.get()
                .uri("/api/gateway/status")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists(RequestTracingFilter.CORRELATION_ID_HEADER)
                .expectBody(new ParameterizedTypeReference<ApiResponse<GatewayStatusResponse>>() {})
                .value(response -> {
                    assertThat(response).isNotNull();
                    assertThat(response.isSuccess()).isTrue();
                    assertThat(response.getData().getStatus()).isEqualTo("UP");
                    assertThat(response.getData().getTotalRoutes()).isGreaterThan(0);
                });
    }

    @Test
    @DisplayName("Verify Protected Endpoint Rejects Request Without JWT (HTTP 401)")
    void shouldRejectProtectedRouteWithoutToken() {
        webTestClient.get()
                .uri("/api/customers/profile")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().exists(RequestTracingFilter.CORRELATION_ID_HEADER)
                .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                .value(response -> {
                    assertThat(response).isNotNull();
                    assertThat(response.isSuccess()).isFalse();
                    assertThat(response.getMessage()).contains("Missing or invalid Authorization header");
                });
    }

    @Test
    @DisplayName("Verify Actuator Health Endpoint returns HTTP 200 UP")
    void shouldReturnActuatorHealthUp() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("\"status\":\"UP\""));
    }
}
