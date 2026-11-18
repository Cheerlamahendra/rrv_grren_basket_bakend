package com.rrvgreenbasket.order.client.impl;

import com.rrvgreenbasket.order.client.ProductServiceClient;
import com.rrvgreenbasket.order.client.dto.ProductDetailClientResponse;
import com.rrvgreenbasket.order.client.dto.ServiceApiResponse;
import com.rrvgreenbasket.order.exception.ExternalServiceException;
import com.rrvgreenbasket.order.exception.OrderValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class ProductServiceClientImpl implements ProductServiceClient {

    private final RestClient restClient;

    @Autowired
    public ProductServiceClientImpl(
            @Value("${app.services.product-service-url:http://localhost:8084}") String productServiceUrl,
            @Value("${app.client.connect-timeout-ms:3000}") int connectTimeout,
            @Value("${app.client.read-timeout-ms:5000}") int readTimeout) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        this.restClient = RestClient.builder()
                .baseUrl(productServiceUrl.replaceAll("/+$", ""))
                .requestFactory(factory)
                .build();
    }

    public ProductServiceClientImpl(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public ProductDetailClientResponse getProductDetails(String productPublicId) {
        log.debug("Fetching live product details: {}", productPublicId);
        try {
            ServiceApiResponse<ProductDetailClientResponse> response = restClient.get()
                    .uri("/api/products/{publicId}", productPublicId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null || !Boolean.TRUE.equals(response.success()) || response.data() == null) {
                throw new OrderValidationException("Product details unavailable for ID: " + productPublicId);
            }
            return response.data();
        } catch (HttpClientErrorException.NotFound e) {
            throw new OrderValidationException("Product does not exist in catalog: " + productPublicId);
        } catch (Exception e) {
            log.error("Product Service call failed for {}: {}", productPublicId, e.getMessage());
            throw new ExternalServiceException("Product Service is currently unavailable: " + e.getMessage(), e);
        }
    }
}
