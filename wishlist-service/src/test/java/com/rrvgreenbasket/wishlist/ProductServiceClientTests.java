package com.rrvgreenbasket.wishlist;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.wishlist.client.ProductServiceClientImpl;
import com.rrvgreenbasket.wishlist.dto.client.ProductDto;
import com.rrvgreenbasket.wishlist.exception.DownstreamServiceException;
import com.rrvgreenbasket.wishlist.exception.InvalidRequestException;
import com.rrvgreenbasket.wishlist.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductServiceClient Unit Tests")
class ProductServiceClientTests {

    @Mock
    private RestTemplate restTemplate;

    private ProductServiceClientImpl productServiceClient;

    private static final String ACTIVE_PRODUCT_ID = "660e8400-e29b-41d4-a716-446655440001";
    private static final String NOT_FOUND_PRODUCT_ID = "660e8400-e29b-41d4-a716-446655440002";
    private static final String INACTIVE_PRODUCT_ID = "660e8400-e29b-41d4-a716-446655440003";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        productServiceClient = new ProductServiceClientImpl(
                restTemplate,
                "http://localhost:8084",
                "c8f7e2d1a5b49382716054321fedcba9876543210abcdef0123456789abcdef0",
                false, // mock fallback disabled to test real downstream error handling
                objectMapper
        );
    }

    @Test
    @DisplayName("Should successfully validate active product from Product Service")
    void testValidateProductForWishlist_Success() {
        String jsonResponse = """
            {
                "success": true,
                "data": {
                    "publicId": "660e8400-e29b-41d4-a716-446655440001",
                    "name": "Fresh Organic Tomato",
                    "status": "ACTIVE",
                    "adminEnabled": true,
                    "available": true,
                    "pricePerKg": 40.00
                }
            }
            """;

        when(restTemplate.exchange(
                eq("http://localhost:8084/api/products/" + ACTIVE_PRODUCT_ID),
                eq(HttpMethod.GET),
                any(),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok(jsonResponse));

        ProductDto product = productServiceClient.validateProductForWishlist(ACTIVE_PRODUCT_ID);

        assertNotNull(product);
        assertEquals(ACTIVE_PRODUCT_ID, product.getPublicId());
        assertEquals("Fresh Organic Tomato", product.getName());
        assertTrue(product.isActiveAndAvailable());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when Product Service returns 404")
    void testValidateProductForWishlist_NotFound() {
        when(restTemplate.exchange(
                eq("http://localhost:8084/api/products/" + NOT_FOUND_PRODUCT_ID),
                eq(HttpMethod.GET),
                any(),
                eq(String.class)
        )).thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, null, StandardCharsets.UTF_8));

        assertThrows(ResourceNotFoundException.class, () ->
                productServiceClient.validateProductForWishlist(NOT_FOUND_PRODUCT_ID));
    }

    @Test
    @DisplayName("Should throw InvalidRequestException when product is INACTIVE")
    void testValidateProductForWishlist_Inactive() {
        String jsonResponse = """
            {
                "success": true,
                "data": {
                    "publicId": "660e8400-e29b-41d4-a716-446655440003",
                    "name": "Inactive Product",
                    "status": "INACTIVE",
                    "adminEnabled": false,
                    "available": false
                }
            }
            """;

        when(restTemplate.exchange(
                eq("http://localhost:8084/api/products/" + INACTIVE_PRODUCT_ID),
                eq(HttpMethod.GET),
                any(),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok(jsonResponse));

        assertThrows(InvalidRequestException.class, () ->
                productServiceClient.validateProductForWishlist(INACTIVE_PRODUCT_ID));
    }

    @Test
    @DisplayName("Should throw DownstreamServiceException when Product Service is unavailable and mock fallback disabled")
    void testValidateProductForWishlist_ServiceUnavailable() {
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(),
                eq(String.class)
        )).thenThrow(new ResourceAccessException("Connection timed out"));

        assertThrows(DownstreamServiceException.class, () ->
                productServiceClient.validateProductForWishlist(ACTIVE_PRODUCT_ID));
    }

    @Test
    @DisplayName("Should return placeholder in getProductDetails if product 404s without throwing")
    void testGetProductDetails_NotFound_ResilientFallback() {
        when(restTemplate.exchange(
                eq("http://localhost:8084/api/products/" + NOT_FOUND_PRODUCT_ID),
                eq(HttpMethod.GET),
                any(),
                eq(String.class)
        )).thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, null, StandardCharsets.UTF_8));

        ProductDto dto = productServiceClient.getProductDetails(NOT_FOUND_PRODUCT_ID);

        assertNotNull(dto);
        assertEquals(NOT_FOUND_PRODUCT_ID, dto.getPublicId());
        assertEquals("DELETED", dto.getStatus());
        assertFalse(dto.getAvailable());
    }

    @Test
    @DisplayName("Should retrieve batch product details")
    void testGetProductsBatch() {
        String jsonResponse = """
            {
                "success": true,
                "data": {
                    "publicId": "660e8400-e29b-41d4-a716-446655440001",
                    "name": "Fresh Organic Tomato",
                    "status": "ACTIVE",
                    "available": true
                }
            }
            """;

        when(restTemplate.exchange(
                eq("http://localhost:8084/api/products/" + ACTIVE_PRODUCT_ID),
                eq(HttpMethod.GET),
                any(),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok(jsonResponse));

        when(restTemplate.exchange(
                eq("http://localhost:8084/api/products/" + NOT_FOUND_PRODUCT_ID),
                eq(HttpMethod.GET),
                any(),
                eq(String.class)
        )).thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, null, StandardCharsets.UTF_8));

        Map<String, ProductDto> batch = productServiceClient.getProductsBatch(List.of(ACTIVE_PRODUCT_ID, NOT_FOUND_PRODUCT_ID));

        assertNotNull(batch);
        assertEquals(2, batch.size());
        assertTrue(batch.containsKey(ACTIVE_PRODUCT_ID));
        assertTrue(batch.containsKey(NOT_FOUND_PRODUCT_ID));
    }
}
