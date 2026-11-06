package com.rrvgreenbasket.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.product.client.AuthServiceClient;
import com.rrvgreenbasket.product.dto.request.CreateProductRequest;
import com.rrvgreenbasket.product.dto.response.ProductDetailResponse;
import com.rrvgreenbasket.product.entity.ProductCategory;
import com.rrvgreenbasket.product.entity.ProductS3DeletionOutbox;
import com.rrvgreenbasket.product.enums.ProductStatus;
import com.rrvgreenbasket.product.enums.S3DeletionStatus;
import com.rrvgreenbasket.product.exception.ConflictException;
import com.rrvgreenbasket.product.exception.DependencyServiceUnavailableException;
import com.rrvgreenbasket.product.repository.ProductCategoryRepository;
import com.rrvgreenbasket.product.repository.ProductImageRepository;
import com.rrvgreenbasket.product.repository.ProductRepository;
import com.rrvgreenbasket.product.repository.ProductS3DeletionOutboxRepository;
import com.rrvgreenbasket.product.scheduler.ProductS3DeletionDispatcher;
import com.rrvgreenbasket.product.service.ImageStorageService;
import com.rrvgreenbasket.product.service.ProductDeletionGuard;
import com.rrvgreenbasket.product.service.ProductService;
import com.rrvgreenbasket.product.service.impl.MockImageStorageService;
import com.rrvgreenbasket.product.service.impl.ProductDeletionGuardImpl;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "app.s3.deletion-outbox.enabled=false", // Disable scheduler automatic polling during test, trigger manually
        "spring.autoconfigure.exclude=org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration,org.springframework.cloud.commons.util.InetUtilsAutoConfiguration"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class PermanentProductDeletionTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductCategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private ProductS3DeletionOutboxRepository s3DeletionOutboxRepository;

    @Autowired
    private ProductService productService;

    @Autowired
    private ImageStorageService imageStorageService;

    @Autowired
    private ProductS3DeletionDispatcher s3DeletionDispatcher;

    @MockBean
    private AuthServiceClient authServiceClient;

    @SpyBean
    private ProductDeletionGuard productDeletionGuard;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.security.internal-service-secret}")
    private String internalServiceSecret;

    private String adminToken;
    private String customerToken;
    private ProductCategory fruitsCategory;

    @BeforeEach
    void setUp() {
        s3DeletionOutboxRepository.deleteAll();
        productImageRepository.deleteAll();
        productRepository.deleteAll();

        Mockito.when(authServiceClient.isSessionActive(anyString())).thenReturn(true);
        adminToken = generateJwtToken("admin-user-id", "admin@rrvgreenbasket.com", "ROLE_ADMIN");
        customerToken = generateJwtToken("cust-user-id", "customer@example.com", "ROLE_CUSTOMER");

        fruitsCategory = categoryRepository.findBySlug("fruits")
                .orElseThrow(() -> new IllegalStateException("Fruits category not found in test db"));
    }

    @Test
    @DisplayName("Permanent Deletion: Safely deletes product, cascades to images, queues S3 outbox, and dispatcher deletes from S3")
    void testPermanentDeletionSuccess_WithImagesAndTransactionalOutbox() throws Exception {
        if (imageStorageService instanceof MockImageStorageService mockStorage) {
            mockStorage.getDeletedKeys().clear();
        }

        // Mock guard: validate succeeds (no external dependencies)
        Mockito.doNothing().when(productDeletionGuard).validateProductCanBeDeleted(anyString());

        // 1. Create product with 2 images
        MockMultipartFile img1 = new MockMultipartFile("images", "img1.jpg", "image/jpeg", "image-bytes-1".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile img2 = new MockMultipartFile("images", "img2.jpg", "image/jpeg", "image-bytes-2".getBytes(StandardCharsets.UTF_8));

        CreateProductRequest createReq = CreateProductRequest.builder()
                .categoryId(fruitsCategory.getPublicId())
                .name("Deletable Guava")
                .pricePerKg(new BigDecimal("60.00"))
                .build();

        ProductDetailResponse created = productService.createProduct(createReq, List.of(img1, img2));
        String publicId = created.getPublicId();

        assertThat(created.getImages()).hasSize(2);
        String storageKey1 = created.getImages().get(0).getStorageKey();
        String storageKey2 = created.getImages().get(1).getStorageKey();

        // Verify product and images exist in DB
        assertThat(productRepository.findByPublicId(publicId)).isPresent();
        assertThat(productImageRepository.countByProduct_Id(productRepository.findByPublicId(publicId).get().getId())).isEqualTo(2);

        // 2. Call DELETE /api/admin/products/{publicId}/permanent
        mockMvc.perform(delete("/api/admin/products/" + publicId + "/permanent")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Product permanently deleted successfully"));

        // 3. Verify product is gone from DB
        assertThat(productRepository.findByPublicId(publicId)).isEmpty();

        // 4. Verify category is NOT deleted
        assertThat(categoryRepository.findByPublicId(fruitsCategory.getPublicId())).isPresent();

        // 5. Verify S3 deletion outbox entries created with status PENDING
        List<ProductS3DeletionOutbox> outboxEntries = s3DeletionOutboxRepository.findByProductPublicId(publicId);
        assertThat(outboxEntries).hasSize(2);
        for (ProductS3DeletionOutbox entry : outboxEntries) {
            assertThat(entry.getStatus()).isEqualTo(S3DeletionStatus.PENDING);
            assertThat(entry.getStorageKey()).isIn(storageKey1, storageKey2);
        }

        // 6. Trigger S3 Deletion Dispatcher background worker
        s3DeletionDispatcher.processPendingDeletions();

        // 7. Verify outbox records marked as COMPLETED and images deleted from MockImageStorage
        List<ProductS3DeletionOutbox> updatedEntries = s3DeletionOutboxRepository.findByProductPublicId(publicId);
        for (ProductS3DeletionOutbox entry : updatedEntries) {
            assertThat(entry.getStatus()).isEqualTo(S3DeletionStatus.COMPLETED);
            assertThat(entry.getLastError()).isNull();
        }

        if (imageStorageService instanceof MockImageStorageService mockStorage) {
            assertThat(mockStorage.getDeletedKeys()).contains(storageKey1, storageKey2);
        }
    }

    @Test
    @DisplayName("Permanent Deletion: Blocks deletion when Order Service has historical orders (409 Conflict)")
    void testPermanentDeletionBlocked_ByOrderServiceDependencies() throws Exception {
        CreateProductRequest createReq = CreateProductRequest.builder()
                .categoryId(fruitsCategory.getPublicId())
                .name("Ordered Banana")
                .pricePerKg(new BigDecimal("40.00"))
                .build();

        ProductDetailResponse created = productService.createProduct(createReq);
        String publicId = created.getPublicId();

        // Mock guard to simulate Order Service reporting active/historical customer orders
        Mockito.doThrow(new ConflictException("Cannot permanently delete product: historical orders exist for product public ID " + publicId))
                .when(productDeletionGuard).validateProductCanBeDeleted(publicId);

        mockMvc.perform(delete("/api/admin/products/" + publicId + "/permanent")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("historical orders exist")));

        // Product MUST still exist in database
        assertThat(productRepository.findByPublicId(publicId)).isPresent();
        assertThat(s3DeletionOutboxRepository.findByProductPublicId(publicId)).isEmpty();
    }

    @Test
    @DisplayName("Permanent Deletion: Blocks deletion when Inventory Service has stock/movements (409 Conflict)")
    void testPermanentDeletionBlocked_ByInventoryServiceDependencies() throws Exception {
        CreateProductRequest createReq = CreateProductRequest.builder()
                .categoryId(fruitsCategory.getPublicId())
                .name("Inventory Apple")
                .pricePerKg(new BigDecimal("120.00"))
                .build();

        ProductDetailResponse created = productService.createProduct(createReq);
        String publicId = created.getPublicId();

        // Mock guard to simulate Inventory Service reporting positive physical stock
        Mockito.doThrow(new ConflictException("Cannot permanently delete product: active physical stock remains (25.000 KG)"))
                .when(productDeletionGuard).validateProductCanBeDeleted(publicId);

        mockMvc.perform(delete("/api/admin/products/" + publicId + "/permanent")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("active physical stock remains")));

        // Product MUST still exist in database
        assertThat(productRepository.findByPublicId(publicId)).isPresent();
    }

    @Test
    @DisplayName("Permanent Deletion: Fails safely with 503 Service Unavailable when dependency service is down")
    void testPermanentDeletionFailsSafely_WhenDependencyServiceDown() throws Exception {
        CreateProductRequest createReq = CreateProductRequest.builder()
                .categoryId(fruitsCategory.getPublicId())
                .name("Safe Check Pear")
                .pricePerKg(new BigDecimal("90.00"))
                .build();

        ProductDetailResponse created = productService.createProduct(createReq);
        String publicId = created.getPublicId();

        // Mock guard to simulate connection failure to downstream dependency service
        Mockito.doThrow(new DependencyServiceUnavailableException("Order Service is unavailable. Permanent product deletion blocked for safety."))
                .when(productDeletionGuard).validateProductCanBeDeleted(publicId);

        mockMvc.perform(delete("/api/admin/products/" + publicId + "/permanent")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Order Service is unavailable")));

        // Product MUST NOT be deleted
        assertThat(productRepository.findByPublicId(publicId)).isPresent();
    }

    @Test
    @DisplayName("Soft Delete: Standard DELETE /api/admin/products/{publicId} remains intact and archives without deleting")
    void testStandardSoftDeleteRemainsIntact() throws Exception {
        CreateProductRequest createReq = CreateProductRequest.builder()
                .categoryId(fruitsCategory.getPublicId())
                .name("Archivable Papaya")
                .pricePerKg(new BigDecimal("50.00"))
                .build();

        ProductDetailResponse created = productService.createProduct(createReq);
        String publicId = created.getPublicId();

        mockMvc.perform(delete("/api/admin/products/" + publicId)
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Product archived successfully"));

        // Product still exists in DB with ARCHIVED status and adminEnabled = false
        var product = productRepository.findByPublicId(publicId).orElseThrow();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ARCHIVED);
        assertThat(product.getAdminEnabled()).isFalse();

        // No S3 deletion outbox entries should have been created
        assertThat(s3DeletionOutboxRepository.findByProductPublicId(publicId)).isEmpty();
    }

    @Test
    @DisplayName("Permanent Deletion: Unauthenticated request returns 401 Unauthorized")
    void testPermanentDeletionUnauthenticated() throws Exception {
        mockMvc.perform(delete("/api/admin/products/" + UUID.randomUUID() + "/permanent")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Permanent Deletion: Customer role returns 403 Forbidden")
    void testPermanentDeletionForbiddenForCustomer() throws Exception {
        mockMvc.perform(delete("/api/admin/products/" + UUID.randomUUID() + "/permanent")
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Permanent Deletion: Non-existent product public ID returns 404")
    void testPermanentDeletionNotFound() throws Exception {
        mockMvc.perform(delete("/api/admin/products/" + UUID.randomUUID() + "/permanent")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Unit Test: ProductDeletionGuardImpl handles responses and exceptions correctly")
    void testProductDeletionGuardImplDirectly() {
        RestTemplate mockRestTemplate = Mockito.mock(RestTemplate.class);
        org.springframework.boot.web.client.RestTemplateBuilder mockBuilder = Mockito.mock(org.springframework.boot.web.client.RestTemplateBuilder.class);
        Mockito.when(mockBuilder.setConnectTimeout(any())).thenReturn(mockBuilder);
        Mockito.when(mockBuilder.setReadTimeout(any())).thenReturn(mockBuilder);
        Mockito.when(mockBuilder.build()).thenReturn(mockRestTemplate);

        ProductDeletionGuardImpl guard = new ProductDeletionGuardImpl(
                mockBuilder,
                "http://localhost:8082",
                "http://localhost:8085",
                internalServiceSecret,
                objectMapper
        );

        String prodId = UUID.randomUUID().toString();

        // Case A: Order Service returns 200 with canDelete = false
        Mockito.when(mockRestTemplate.exchange(
                eq("http://localhost:8082/api/internal/orders/products/" + prodId + "/deletion-check"),
                eq(HttpMethod.GET),
                any(),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok("{\"success\":true,\"data\":{\"canDelete\":false,\"reason\":\"Product is in 5 orders\"}}"));

        assertThatThrownBy(() -> guard.validateProductCanBeDeleted(prodId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Product is in 5 orders");

        // Case B: Order Service returns 200 with canDelete = true, Inventory Service throws connection refused
        Mockito.when(mockRestTemplate.exchange(
                eq("http://localhost:8082/api/internal/orders/products/" + prodId + "/deletion-check"),
                eq(HttpMethod.GET),
                any(),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok("{\"success\":true,\"data\":{\"canDelete\":true}}"));

        Mockito.when(mockRestTemplate.exchange(
                eq("http://localhost:8085/api/internal/inventory/products/" + prodId + "/deletion-check"),
                eq(HttpMethod.GET),
                any(),
                eq(String.class)
        )).thenThrow(new ResourceAccessException("Connection refused: connect"));

        assertThatThrownBy(() -> guard.validateProductCanBeDeleted(prodId))
                .isInstanceOf(DependencyServiceUnavailableException.class)
                .hasMessageContaining("Inventory Service is unavailable");

        // Case C: Both services return canDelete = true
        Mockito.when(mockRestTemplate.exchange(
                eq("http://localhost:8085/api/internal/inventory/products/" + prodId + "/deletion-check"),
                eq(HttpMethod.GET),
                any(),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok("{\"success\":true,\"data\":{\"canDelete\":true}}"));

        guard.validateProductCanBeDeleted(prodId); // should pass without exception
    }

    private String generateJwtToken(String userId, String email, String role) {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);

        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .claim("role", role)
                .claim("sid", UUID.randomUUID().toString())
                .issuedAt(new Date(now))
                .expiration(new Date(now + 3600000))
                .signWith(key)
                .compact();
    }
}
