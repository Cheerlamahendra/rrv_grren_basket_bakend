package com.rrvgreenbasket.cart.client.impl;

import com.rrvgreenbasket.cart.client.ProductServiceClient;
import com.rrvgreenbasket.cart.dto.client.ProductAvailabilityResponse;
import com.rrvgreenbasket.cart.dto.client.ProductDetailResponse;
import com.rrvgreenbasket.cart.dto.client.ServiceApiResponse;
import com.rrvgreenbasket.cart.exception.ProductNotFoundException;
import com.rrvgreenbasket.cart.exception.ProductUnavailableException;
import com.rrvgreenbasket.cart.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
public class ProductServiceClientImpl implements ProductServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ProductServiceClientImpl.class);

    private final RestClient restClient;

    @Autowired
    public ProductServiceClientImpl(
            @Value("${app.services.product-service-url:http://localhost:8084}") String productServiceBaseUrl,
            @Value("${app.client.connect-timeout-ms:2000}") int connectTimeout,
            @Value("${app.client.read-timeout-ms:3000}") int readTimeout) {

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);

        this.restClient = RestClient.builder()
                .baseUrl(productServiceBaseUrl.replaceAll("/+$", ""))
                .requestFactory(requestFactory)
                .build();
    }

    public ProductServiceClientImpl(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public ProductDetailResponse getProductByPublicId(String productPublicId) {
        if (productPublicId == null || productPublicId.isBlank()) {
            throw new IllegalArgumentException("Product public ID is required");
        }

        try {
            log.debug("Fetching product details from Product Service: {}", productPublicId);

            ServiceApiResponse<ProductDetailResponse> response = restClient.get()
                    .uri("/api/products/{publicId}", productPublicId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null || !Boolean.TRUE.equals(response.success()) || response.data() == null) {
                throw new ServiceUnavailableException("Invalid response structure received from Product Service");
            }

            ProductDetailResponse product = response.data();

            if (product.name() == null || product.pricePerKg() == null) {
                throw new ServiceUnavailableException("Product response is missing mandatory fields (name or pricePerKg)");
            }

            return product;

        } catch (HttpClientErrorException.NotFound ex) {
            log.warn("Product not found in Product Service: {}", productPublicId);
            throw new ProductNotFoundException("Product with ID '" + productPublicId + "' does not exist in catalog");
        } catch (HttpClientErrorException ex) {
            log.warn("Product Service client error for product {}: {}", productPublicId, ex.getMessage());
            throw new ProductUnavailableException("Product is currently unavailable: " + ex.getResponseBodyAsString());
        } catch (HttpServerErrorException ex) {
            log.error("Product Service server error: {}", ex.getMessage());
            throw new ServiceUnavailableException("Product Service server error: " + ex.getStatusCode(), ex);
        } catch (ResourceAccessException ex) {
            log.error("Product Service timeout or network error: {}", ex.getMessage());
            throw new ServiceUnavailableException("Product Service is currently unreachable or timed out.", ex);
        }
    }

    @Override
    public ProductAvailabilityResponse checkProductAvailability(String productPublicId, BigDecimal quantity) {
        if (productPublicId == null || productPublicId.isBlank()) {
            throw new IllegalArgumentException("Product public ID is required");
        }

        try {
            log.debug("Checking availability with Product Service: product={}, quantity={}", productPublicId, quantity);

            ServiceApiResponse<ProductAvailabilityResponse> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/products/{publicId}/availability")
                            .queryParam("quantity", quantity != null ? quantity : BigDecimal.ONE)
                            .build(productPublicId))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null || !Boolean.TRUE.equals(response.success()) || response.data() == null) {
                throw new ServiceUnavailableException("Invalid response received from Product Service");
            }

            return response.data();

        } catch (HttpClientErrorException.NotFound ex) {
            log.warn("Product not found when checking availability: {}", productPublicId);
            throw new ProductNotFoundException("Product with ID '" + productPublicId + "' does not exist in catalog");
        } catch (HttpClientErrorException ex) {
            log.warn("Product Service availability client error: {}", ex.getMessage());
            throw new ProductUnavailableException("Product availability check failed: " + ex.getResponseBodyAsString());
        } catch (HttpServerErrorException ex) {
            log.error("Product Service availability server error: {}", ex.getMessage());
            throw new ServiceUnavailableException("Product Service encountered an error checking availability.", ex);
        } catch (ResourceAccessException ex) {
            log.error("Product Service availability timeout: {}", ex.getMessage());
            throw new ServiceUnavailableException("Product Service is currently unreachable or timed out.", ex);
        }
    }
}
