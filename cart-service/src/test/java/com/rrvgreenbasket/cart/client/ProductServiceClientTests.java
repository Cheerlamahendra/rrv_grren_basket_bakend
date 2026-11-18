package com.rrvgreenbasket.cart.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.cart.client.impl.ProductServiceClientImpl;
import com.rrvgreenbasket.cart.dto.client.ProductDetailResponse;
import com.rrvgreenbasket.cart.dto.client.ServiceApiResponse;
import com.rrvgreenbasket.cart.exception.ProductNotFoundException;
import com.rrvgreenbasket.cart.exception.ProductUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@DisplayName("ProductServiceClient Tests")
class ProductServiceClientTests {

    private ProductServiceClientImpl client;
    private MockRestServiceServer mockServer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8084");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new ProductServiceClientImpl(builder.build());
    }

    @Test
    @DisplayName("Successfully fetches product details")
    void testGetProductByPublicId_Success() throws Exception {
        ProductDetailResponse mockProduct = new ProductDetailResponse(
                "prod-1", "Organic Spinach", new BigDecimal("45.00"),
                BigDecimal.ONE, "KG", new BigDecimal("45.00"),
                "http://example.com/spinach.png", "ACTIVE", true, false, true, "IN_STOCK"
        );
        ServiceApiResponse<ProductDetailResponse> apiResponse =
                new ServiceApiResponse<>(true, "Success", mockProduct, null);

        mockServer.expect(requestTo("http://localhost:8084/api/products/prod-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(objectMapper.writeValueAsString(apiResponse), MediaType.APPLICATION_JSON));

        ProductDetailResponse result = client.getProductByPublicId("prod-1");

        assertNotNull(result);
        assertEquals("Organic Spinach", result.name());
        assertEquals(new BigDecimal("45.00"), result.pricePerKg());
        mockServer.verify();
    }

    @Test
    @DisplayName("Throws ProductNotFoundException when Product Service returns 404")
    void testGetProductByPublicId_NotFound() {
        mockServer.expect(requestTo("http://localhost:8084/api/products/missing-prod"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(ProductNotFoundException.class, () -> client.getProductByPublicId("missing-prod"));
        mockServer.verify();
    }
}
