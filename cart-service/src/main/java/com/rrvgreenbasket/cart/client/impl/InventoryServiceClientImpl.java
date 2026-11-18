package com.rrvgreenbasket.cart.client.impl;

import com.rrvgreenbasket.cart.client.InventoryServiceClient;
import com.rrvgreenbasket.cart.dto.client.ServiceApiResponse;
import com.rrvgreenbasket.cart.dto.client.StockAvailabilityResponse;
import com.rrvgreenbasket.cart.exception.ProductNotFoundException;
import com.rrvgreenbasket.cart.exception.ProductUnavailableException;
import com.rrvgreenbasket.cart.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@Component
public class InventoryServiceClientImpl implements InventoryServiceClient {

    private static final Logger log = LoggerFactory.getLogger(InventoryServiceClientImpl.class);

    private final RestClient restClient;

    public InventoryServiceClientImpl(
            @Value("${app.services.inventory-service-url:http://localhost:8085}") String inventoryServiceBaseUrl,
            @Value("${app.client.connect-timeout-ms:2000}") int connectTimeout,
            @Value("${app.client.read-timeout-ms:3000}") int readTimeout) {

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);

        this.restClient = RestClient.builder()
                .baseUrl(inventoryServiceBaseUrl.replaceAll("/+$", ""))
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public StockAvailabilityResponse checkStockAvailability(String productPublicId, BigDecimal quantity) {
        if (productPublicId == null || productPublicId.isBlank()) {
            throw new IllegalArgumentException("Product public ID is required");
        }

        try {
            log.debug("Checking inventory availability: product={}, quantity={}", productPublicId, quantity);

            ServiceApiResponse<StockAvailabilityResponse> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/inventory/products/{productPublicId}/availability")
                            .queryParam("quantity", quantity != null ? quantity : BigDecimal.ONE)
                            .build(productPublicId))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null || !Boolean.TRUE.equals(response.success()) || response.data() == null) {
                throw new ServiceUnavailableException("Invalid response structure received from Inventory Service");
            }

            StockAvailabilityResponse stock = response.data();

            // Validate mandatory fields
            if (stock.unitOfMeasure() == null || stock.availableQuantity() == null) {
                throw new ServiceUnavailableException("Inventory Service returned incomplete stock data");
            }

            return stock;

        } catch (HttpClientErrorException.NotFound ex) {
            log.warn("Inventory record not found for product: {}", productPublicId);
            throw new ProductNotFoundException("Inventory record not found for product ID: " + productPublicId);
        } catch (HttpClientErrorException ex) {
            log.warn("Inventory Service client error: {}", ex.getMessage());
            throw new ProductUnavailableException("Inventory inquiry failed: " + ex.getResponseBodyAsString());
        } catch (HttpServerErrorException ex) {
            log.error("Inventory Service server error: {}", ex.getMessage());
            throw new ServiceUnavailableException("Inventory Service encountered an error checking stock: " + ex.getStatusCode(), ex);
        } catch (ResourceAccessException ex) {
            log.error("Inventory Service timeout or connection error: {}", ex.getMessage());
            throw new ServiceUnavailableException("Inventory Service is currently unreachable or timed out.", ex);
        }
    }
}
