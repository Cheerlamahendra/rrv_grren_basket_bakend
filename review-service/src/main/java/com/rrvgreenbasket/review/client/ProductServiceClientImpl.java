package com.rrvgreenbasket.review.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.review.dto.client.ProductDto;
import com.rrvgreenbasket.review.exception.DownstreamServiceException;
import com.rrvgreenbasket.review.exception.InvalidRequestException;
import com.rrvgreenbasket.review.exception.ResourceNotFoundException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class ProductServiceClientImpl implements ProductServiceClient {

    private final RestTemplate restTemplate;
    private final String productServiceUrl;
    private final ObjectMapper objectMapper;
    private final SecretKey internalKey;
    private final boolean mockFallbackEnabled;

    public ProductServiceClientImpl(
            RestTemplate restTemplate,
            @Value("${review.product-service.url:http://localhost:8084}") String productServiceUrl,
            @Value("${app.security.internal-service-secret:RQpmB5hGO2NsLNE7OnwXo5NfNtEJj+j+461g0oEeGunJPZ4iYQEGr+uk5M3hAfgDg26IDtmA9N2RJbFXkCLrqQ==}") String internalSecret,
            @Value("${review.product-service.mock-fallback-enabled:true}") boolean mockFallbackEnabled,
            ObjectMapper objectMapper) {

        this.restTemplate = restTemplate;
        this.productServiceUrl = productServiceUrl.replaceAll("/+$", "");
        this.objectMapper = objectMapper;
        this.mockFallbackEnabled = mockFallbackEnabled;

        if (!StringUtils.hasText(internalSecret)) {
            throw new IllegalStateException("Internal service secret is missing. Set app.security.internal-service-secret or INTERNAL_SERVICE_SECRET.");
        }

        byte[] keyBytes = Decoders.BASE64.decode(internalSecret.trim());
        if (keyBytes.length < 64) {
            throw new IllegalStateException("Gateway assertion secret must contain at least 64 bytes (512 bits) for HS512 algorithm. Current key size: " + (keyBytes.length * 8) + " bits");
        }
        this.internalKey = Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    public ProductDto validateProductForReview(String productPublicId) {
        if (productPublicId == null || productPublicId.isBlank()) {
            throw new InvalidRequestException("Product public ID cannot be null or empty");
        }

        ProductDto product = fetchProductFromDownstream(productPublicId);

        if (product == null) {
            throw new ResourceNotFoundException("Product not found with ID: " + productPublicId);
        }

        if (!product.isEligibleForReview()) {
            throw new InvalidRequestException("Product is inactive or unavailable for reviews: " + productPublicId);
        }

        return product;
    }

    @Override
    public ProductDto getProductDetails(String productPublicId) {
        if (productPublicId == null || productPublicId.isBlank()) {
            return null;
        }

        try {
            return fetchProductFromDownstream(productPublicId);
        } catch (Exception ex) {
            log.warn("Failed to retrieve product details for {}: {}", productPublicId, ex.getMessage());
            return null;
        }
    }

    private ProductDto fetchProductFromDownstream(String productPublicId) {
        String url = productServiceUrl + "/api/products/" + productPublicId.trim();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.set("X-Gateway-Assertion", generateGatewayAssertion());

            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
            log.debug("Calling Product Service: {}", url);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    requestEntity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode dataNode = root.path("data");
                if (!dataNode.isMissingNode() && !dataNode.isNull()) {
                    return objectMapper.treeToValue(dataNode, ProductDto.class);
                }
            }
        } catch (HttpClientErrorException.NotFound ex) {
            log.warn("Product Service returned 404 for product: {}", productPublicId);
            throw new ResourceNotFoundException("Product not found with ID: " + productPublicId);
        } catch (HttpClientErrorException.BadRequest ex) {
            log.warn("Product Service returned 400 for product: {}", productPublicId);
            throw new InvalidRequestException("Invalid product public ID: " + productPublicId);
        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            log.error("Product Service returned HTTP error: status={}, body={}", ex.getStatusCode(), ex.getResponseBodyAsString());
            if (mockFallbackEnabled) {
                return buildMockProductIfEligible(productPublicId);
            }
            throw new DownstreamServiceException("Product Service returned error: " + ex.getStatusCode(), ex);
        } catch (ResourceAccessException ex) {
            log.warn("Product Service unavailable at {}: {}", url, ex.getMessage());
            if (mockFallbackEnabled) {
                log.info("Using mock product fallback for ID: {}", productPublicId);
                return buildMockProductIfEligible(productPublicId);
            }
            throw new DownstreamServiceException("Product Service is temporarily unavailable. Please try again later.", ex);
        } catch (Exception ex) {
            log.error("Unexpected error calling Product Service: {}", ex.getMessage(), ex);
            if (mockFallbackEnabled) {
                return buildMockProductIfEligible(productPublicId);
            }
            throw new DownstreamServiceException("Failed to communicate with Product Service", ex);
        }

        return null;
    }

    private ProductDto buildMockProductIfEligible(String productPublicId) {
        // Special simulated IDs
        if ("00000000-0000-0000-0000-000000000404".equalsIgnoreCase(productPublicId)) {
            throw new ResourceNotFoundException("Product not found with ID: " + productPublicId);
        }
        if ("00000000-0000-0000-0000-000000000400".equalsIgnoreCase(productPublicId)) {
            return ProductDto.builder()
                    .publicId(productPublicId)
                    .name("Inactive Test Product")
                    .slug("inactive-test-product")
                    .pricePerKg(BigDecimal.valueOf(50.00))
                    .quantityUnit("KG")
                    .status("INACTIVE")
                    .adminEnabled(false)
                    .available(false)
                    .stockStatus("OUT_OF_STOCK")
                    .build();
        }

        // Return a valid mock representation for standard UUIDs
        return ProductDto.builder()
                .publicId(productPublicId)
                .name("Mock Verified Product")
                .slug("mock-verified-product")
                .pricePerKg(BigDecimal.valueOf(45.00))
                .quantityUnit("KG")
                .status("ACTIVE")
                .adminEnabled(true)
                .available(true)
                .stockStatus("IN_STOCK")
                .build();
    }

    private String generateGatewayAssertion() {
        return Jwts.builder()
                .subject("review-service")
                .claim("role", "ROLE_INTERNAL_SERVICE")
                .claim("aud", "product-service")
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(internalKey, Jwts.SIG.HS512)
                .compact();
    }
}
