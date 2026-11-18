package com.rrvgreenbasket.order.client.impl;

import com.rrvgreenbasket.order.client.DeliveryServiceClient;
import com.rrvgreenbasket.order.client.dto.DeliveryAvailabilityClientResponse;
import com.rrvgreenbasket.order.client.dto.ServiceApiResponse;
import com.rrvgreenbasket.order.exception.ExternalServiceException;
import com.rrvgreenbasket.order.exception.OrderValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
public class DeliveryServiceClientImpl implements DeliveryServiceClient {

    private final RestClient restClient;

    @Autowired
    public DeliveryServiceClientImpl(
            @Value("${app.services.delivery-service-url:http://localhost:8094}") String deliveryServiceUrl,
            @Value("${app.client.connect-timeout-ms:3000}") int connectTimeout,
            @Value("${app.client.read-timeout-ms:5000}") int readTimeout) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        this.restClient = RestClient.builder()
                .baseUrl(deliveryServiceUrl.replaceAll("/+$", ""))
                .requestFactory(factory)
                .build();
    }

    public DeliveryServiceClientImpl(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public DeliveryAvailabilityClientResponse checkAvailability(Double latitude, Double longitude, String address) {
        log.debug("Calling Delivery Service check-availability: lat={}, lng={}", latitude, longitude);
        try {
            Map<String, Object> request = Map.of(
                    "latitude", latitude,
                    "longitude", longitude,
                    "address", address != null ? address : ""
            );

            ServiceApiResponse<DeliveryAvailabilityClientResponse> response = restClient.post()
                    .uri("/api/delivery/check-availability")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null || !Boolean.TRUE.equals(response.success()) || response.data() == null) {
                throw new OrderValidationException("Unable to verify delivery availability for selected coordinates");
            }

            return response.data();
        } catch (OrderValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Delivery Service check-availability call failed: {}", e.getMessage());
            throw new ExternalServiceException("Delivery Service is currently unreachable: " + e.getMessage(), e);
        }
    }
}
