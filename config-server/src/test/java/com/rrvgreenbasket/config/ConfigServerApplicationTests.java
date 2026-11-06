package com.rrvgreenbasket.config;

import com.rrvgreenbasket.config.dto.response.ApiResponse;
import com.rrvgreenbasket.config.dto.response.ConfigServerStatusResponse;
import com.rrvgreenbasket.config.dto.response.HostedConfigSummaryResponse;
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
 * ConfigServerApplicationTests
 *
 * <p><strong>What it does:</strong></p>
 * Integration tests verifying that Spring Cloud Config Server starts up correctly,
 * exposes standard Spring Cloud Config endpoints, and masks sensitive credentials.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConfigServerApplicationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("Verify Spring Application Context Loads with Config Server")
    void contextLoads() {
        // Confirms Spring Cloud Config Server initialization without bean conflicts
    }

    @Test
    @DisplayName("Verify Config Server Status Endpoint returns HTTP 200")
    void shouldReturnConfigServerStatusSuccessfully() {
        String url = "http://localhost:" + port + "/api/config/status";

        ResponseEntity<ApiResponse<ConfigServerStatusResponse>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData().getStatus()).isEqualTo("UP");
        assertThat(response.getBody().getData().getAvailableServices()).contains("auth-service", "product-service");
    }

    @Test
    @DisplayName("Verify Inspection of auth-service dev configuration masks sensitive keys")
    void shouldReturnAuthServiceConfigurationWithMaskedSecrets() {
        String url = "http://localhost:" + port + "/api/config/services/auth-service/dev";

        ResponseEntity<ApiResponse<HostedConfigSummaryResponse>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();

        HostedConfigSummaryResponse data = response.getBody().getData();
        assertThat(data.getApplication()).isEqualTo("auth-service");

        // Verify sensitive keys are masked
        assertThat(data.getSanitizedProperties()).containsKey("app.jwt.secret");
        assertThat(data.getSanitizedProperties().get("app.jwt.secret")).isEqualTo("******");
    }

    @Test
    @DisplayName("Verify Standard Spring Cloud Config Native Endpoint (GET /auth-service/dev)")
    void shouldReturnNativeConfigForAuthService() {
        String url = "http://localhost:" + port + "/auth-service/dev";

        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"name\":\"auth-service\"");
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
