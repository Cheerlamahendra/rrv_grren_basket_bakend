package com.rrvgreenbasket.discovery;

import com.rrvgreenbasket.discovery.dto.response.ApiResponse;
import com.rrvgreenbasket.discovery.dto.response.DiscoveryStatusResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DiscoveryServerApplicationTests
 *
 * <p><strong>What it does:</strong></p>
 * Integration test verifying that the Spring Boot context boots up successfully with
 * the Eureka Server active, and that the management endpoints respond with HTTP 200.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DiscoveryServerApplicationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("Verify Spring Application Context Loads with Eureka Server")
    void contextLoads() {
        // Confirms all beans (EurekaServerContext, DiscoveryService, DiscoveryController) initialize without errors
    }

    @Test
    @DisplayName("Verify Discovery Status Endpoint returns HTTP 200 with Valid Response Envelope")
    void shouldReturnDiscoveryStatusSuccessfully() {
        String url = "http://localhost:" + port + "/api/discovery/status";

        ResponseEntity<ApiResponse<DiscoveryStatusResponse>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isNotNull();
        assertThat(response.getBody().getData().getStatus()).isEqualTo("UP");
    }

    @Test
    @DisplayName("Verify Actuator Health Endpoint returns HTTP 200 UP")
    void shouldReturnActuatorHealthUp() {
        String url = "http://localhost:" + port + "/actuator/health";

        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }
}
