
package com.rrvgreenbasket.cart.client.impl;

import com.rrvgreenbasket.cart.client.InventoryServiceClient;
import com.rrvgreenbasket.cart.dto.client.ServiceApiResponse;
import com.rrvgreenbasket.cart.dto.client.StockAvailabilityResponse;
import com.rrvgreenbasket.cart.exception.ServiceUnavailableException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;

@Component
public class InventoryServiceClientImpl implements InventoryServiceClient {

    private static final Logger log =
            LoggerFactory.getLogger(InventoryServiceClientImpl.class);

    private final RestClient restClient;

    public InventoryServiceClientImpl(
            @Value("${app.services.inventory-service-url:http://localhost:8085}")
            String inventoryServiceBaseUrl) {

        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(2000);
        requestFactory.setReadTimeout(3000);

        this.restClient = RestClient.builder()
                .baseUrl(inventoryServiceBaseUrl.replaceAll("/+$", ""))
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public StockAvailabilityResponse checkStockAvailability(
            String productPublicId,
            BigDecimal quantity) {

        try {
            log.debug(
                    "Checking inventory availability: product={}, quantity={}",
                    productPublicId,
                    quantity
            );

            ServiceApiResponse<StockAvailabilityResponse> response =
                    restClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/api/inventory/products/{productPublicId}/availability")
                                    .queryParam("quantity", quantity)
                                    .build(productPublicId))
                            .retrieve()
                            .body(new ParameterizedTypeReference<>() {});

            if (response == null
                    || !Boolean.TRUE.equals(response.success())
                    || response.data() == null) {

                throw new ServiceUnavailableException(
                        "Invalid response received from Inventory Service"
                );
            }

            return response.data();

        } catch (ResourceAccessException ex) {
            log.error("Inventory Service connection or timeout failure", ex);
            throw new ServiceUnavailableException(
                    "Inventory Service is currently unavailable. Please try again later.",
                    ex
            );
        } catch (RestClientException ex) {
            log.error("Inventory Service communication failure", ex);
            throw new ServiceUnavailableException(
                    "Unable to communicate with Inventory Service.",
                    ex
            );
        }
    }
}
