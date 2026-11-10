
package com.rrvgreenbasket.cart.client.impl;

import com.rrvgreenbasket.cart.client.ProductServiceClient;
import com.rrvgreenbasket.cart.dto.client.ProductAvailabilityResponse;
import com.rrvgreenbasket.cart.dto.client.ProductDetailResponse;
import com.rrvgreenbasket.cart.dto.client.ServiceApiResponse;
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
public class ProductServiceClientImpl implements ProductServiceClient {

    private static final Logger log =
            LoggerFactory.getLogger(ProductServiceClientImpl.class);

    private final RestClient restClient;

    public ProductServiceClientImpl(
            @Value("${app.services.product-service-url:http://localhost:8084}")
            String productServiceBaseUrl) {

        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(2000);
        requestFactory.setReadTimeout(3000);

        this.restClient = RestClient.builder()
                .baseUrl(productServiceBaseUrl.replaceAll("/+$", ""))
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public ProductDetailResponse getProductByPublicId(String productPublicId) {

        try {
            log.debug("Fetching product details: {}", productPublicId);

            ServiceApiResponse<ProductDetailResponse> response =
                    restClient.get()
                            .uri("/api/products/{publicId}", productPublicId)
                            .retrieve()
                            .body(new ParameterizedTypeReference<>() {});

            if (response == null || !Boolean.TRUE.equals(response.success())
                    || response.data() == null) {
                throw new ServiceUnavailableException(
                        "Invalid response received from Product Service"
                );
            }

            return response.data();

        } catch (ResourceAccessException ex) {
            log.error("Product Service connection or timeout failure", ex);
            throw new ServiceUnavailableException(
                    "Product Service is currently unavailable. Please try again later.",
                    ex
            );
        } catch (RestClientException ex) {
            log.error("Product Service communication failure", ex);
            throw new ServiceUnavailableException(
                    "Unable to communicate with Product Service.",
                    ex
            );
        }
    }

    @Override
    public ProductAvailabilityResponse checkProductAvailability(
            String productPublicId,
            BigDecimal quantity) {

        try {
            log.debug(
                    "Checking product availability: product={}, quantity={}",
                    productPublicId,
                    quantity
            );

            ServiceApiResponse<ProductAvailabilityResponse> response =
                    restClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/api/products/{publicId}/availability")
                                    .queryParam("quantity", quantity)
                                    .build(productPublicId))
                            .retrieve()
                            .body(new ParameterizedTypeReference<>() {});

            if (response == null || !Boolean.TRUE.equals(response.success())
                    || response.data() == null) {
                throw new ServiceUnavailableException(
                        "Invalid response received from Product Service"
                );
            }

            return response.data();

        } catch (ResourceAccessException ex) {
            log.error("Product Service availability timeout or connection failure", ex);
            throw new ServiceUnavailableException(
                    "Product Service is currently unavailable. Please try again later.",
                    ex
            );
        } catch (RestClientException ex) {
            log.error("Product availability communication failure", ex);
            throw new ServiceUnavailableException(
                    "Unable to check product availability.",
                    ex
            );
        }
    }
}
