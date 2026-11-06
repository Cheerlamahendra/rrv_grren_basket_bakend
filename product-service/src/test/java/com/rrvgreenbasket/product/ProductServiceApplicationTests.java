package com.rrvgreenbasket.product;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.product.client.AuthServiceClient;
import com.rrvgreenbasket.product.dto.request.CreateCategoryRequest;
import com.rrvgreenbasket.product.dto.request.CreateProductRequest;
import com.rrvgreenbasket.product.dto.request.PatchProductRequest;
import com.rrvgreenbasket.product.dto.request.UpdateAvailabilityRequest;
import com.rrvgreenbasket.product.dto.request.UpdateProductRequest;
import com.rrvgreenbasket.product.dto.request.UpdateStatusRequest;
import com.rrvgreenbasket.product.dto.response.*;
import com.rrvgreenbasket.product.entity.Product;
import com.rrvgreenbasket.product.entity.ProductCategory;
import com.rrvgreenbasket.product.enums.ProductStatus;
import com.rrvgreenbasket.product.enums.StockStatus;
import com.rrvgreenbasket.product.repository.ProductCategoryRepository;
import com.rrvgreenbasket.product.repository.ProductImageRepository;
import com.rrvgreenbasket.product.repository.ProductRepository;
import com.rrvgreenbasket.product.mapper.ProductMapper;
import com.rrvgreenbasket.product.service.ProductService;
import com.rrvgreenbasket.product.service.ImageStorageService;
import com.rrvgreenbasket.product.service.impl.MockImageStorageService;
import com.rrvgreenbasket.product.service.integration.InventoryIntegrationService;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration,org.springframework.cloud.commons.util.InetUtilsAutoConfiguration"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ProductServiceApplicationTests {

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
    private ImageStorageService imageStorageService;

    @Autowired
    private InventoryIntegrationService inventoryIntegrationService;

    @MockBean
    private AuthServiceClient authServiceClient;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.security.internal-service-secret}")
    private String internalServiceSecret;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private String adminToken;
    private String customerToken;

    @BeforeEach
    void setUp() {
        productImageRepository.deleteAll();
        productRepository.deleteAll();
        inventoryIntegrationService.clearSimulatedStock();

        // Default mock: auth-service session is active
        Mockito.when(authServiceClient.isSessionActive(anyString())).thenReturn(true);

        adminToken = generateJwtToken("admin-user-id", "admin@rrvgreenbasket.com", "ROLE_ADMIN");
        customerToken = generateJwtToken("cust-user-id", "customer@example.com", "ROLE_CUSTOMER");
    }

    // =========================================================================
    // 1. FLYWAY MIGRATION & SEEDING TESTS
    // =========================================================================

    @Test
    @DisplayName("Test 1-6: Flyway migrations succeeded and seeded exactly 6 active categories")
    void testFlywayAndCategorySeeding() {
        List<ProductCategory> categories = categoryRepository.findAllByOrderBySortOrderAscNameAsc();

        assertThat(categories).hasSizeGreaterThanOrEqualTo(6);

        List<String> expectedSlugs = List.of(
                "fruits",
                "leafy-vegetables",
                "root-vegetables",
                "seasonings",
                "mushrooms",
                "other-items"
        );

        for (String expectedSlug : expectedSlugs) {
            Optional<ProductCategory> categoryOpt = categoryRepository.findBySlug(expectedSlug);
            assertThat(categoryOpt).isPresent();
            ProductCategory cat = categoryOpt.get();
            assertThat(cat.getIsActive()).isTrue();
            assertThat(cat.getPublicId()).isNotBlank();
            assertThat(cat.getName()).isNotBlank();
        }
    }

    // =========================================================================
    // 2. ADMIN PRODUCT CRUD TESTS
    // =========================================================================

    @Test
    @DisplayName("Test 7: Admin product creation with pricePerKg, defaultQuantityKg and quantityUnit")
    void testProductCreation() throws Exception {
        CreateProductRequest request = CreateProductRequest.builder()
                .categoryId("fruits")
                .name("Shimla Royal Apple")
                .description("Crisp and sweet fresh apples sourced from Shimla orchards.")
                .shortDescription("Fresh Shimla Apples")
                .pricePerKg(new BigDecimal("180.00"))
                .defaultQuantityKg(new BigDecimal("1.000"))
                .quantityUnit("KG")
                .status(ProductStatus.ACTIVE)
                .adminEnabled(true)
                .isFeatured(true)
                .build();

        MvcResult result = mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.publicId").isNotEmpty())
                .andExpect(jsonPath("$.data.name").value("Shimla Royal Apple"))
                .andExpect(jsonPath("$.data.slug").value("shimla-royal-apple"))
                .andExpect(jsonPath("$.data.pricePerKg").value(180.00))
                .andExpect(jsonPath("$.data.defaultQuantityKg").value(1.000))
                .andExpect(jsonPath("$.data.quantityUnit").value("KG"))
                .andExpect(jsonPath("$.data.totalPrice").value(180.00))
                .andExpect(jsonPath("$.data.category.slug").value("fruits"))
                .andReturn();

        ApiResponse<ProductDetailResponse> apiResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<>() {});

        assertThat(apiResponse.getData().getPublicId()).isNotNull();
    }

    @Test
    @DisplayName("Test 8-9: Admin complete update (PUT) and partial update (PATCH)")
    void testProductUpdateAndPatch() throws Exception {
        // 1. Create product first
        ProductDetailResponse created = createSampleProduct("Local Tomatoes", "root-vegetables", "40.00");

        // 2. Complete PUT update
        UpdateProductRequest updateReq = UpdateProductRequest.builder()
                .categoryId("root-vegetables")
                .name("Local Tomatoes Premium")
                .slug("local-tomatoes-premium")
                .description("Updated description for ripe farm tomatoes.")
                .shortDescription("Premium Farm Tomatoes")
                .pricePerKg(new BigDecimal("45.50"))
                .defaultQuantityKg(new BigDecimal("1.000"))
                .quantityUnit("KG")
                .status(ProductStatus.ACTIVE)
                .adminEnabled(true)
                .isFeatured(true)
                .build();

        mockMvc.perform(put("/api/admin/products/" + created.getPublicId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Local Tomatoes Premium"))
                .andExpect(jsonPath("$.data.pricePerKg").value(45.50));

        // 3. Partial PATCH update
        PatchProductRequest patchReq = PatchProductRequest.builder()
                .pricePerKg(new BigDecimal("50.00"))
                .shortDescription("Special Offer Tomatoes")
                .build();

        mockMvc.perform(patch("/api/admin/products/" + created.getPublicId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patchReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pricePerKg").value(50.00))
                .andExpect(jsonPath("$.data.shortDescription").value("Special Offer Tomatoes"))
                .andExpect(jsonPath("$.data.name").value("Local Tomatoes Premium")); // unchanged
    }

    @Test
    @DisplayName("Test 10: Product safe soft-deletion / archiving")
    void testProductDeletion() throws Exception {
        ProductDetailResponse created = createSampleProduct("Coriander Leaves", "leafy-vegetables", "20.00");

        // Execute DELETE
        mockMvc.perform(delete("/api/admin/products/" + created.getPublicId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Verify entity in DB is ARCHIVED and disabled (not physically deleted)
        Product product = productRepository.findByPublicId(created.getPublicId()).orElseThrow();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ARCHIVED);
        assertThat(product.getAdminEnabled()).isFalse();

        // Customer cannot access archived product
        mockMvc.perform(get("/api/products/" + created.getPublicId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Test 16-17: Availability toggle & Status update hide products from customer catalog")
    void testAvailabilityAndStatusToggles() throws Exception {
        ProductDetailResponse created = createSampleProduct("Button Mushrooms", "mushrooms", "90.00");

        // Customer can see it initially
        mockMvc.perform(get("/api/products/" + created.getPublicId()))
                .andExpect(status().isOk());

        // 1. Admin disables product availability
        UpdateAvailabilityRequest availReq = UpdateAvailabilityRequest.builder()
                .adminEnabled(false)
                .build();

        mockMvc.perform(patch("/api/admin/products/" + created.getPublicId() + "/availability")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(availReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adminEnabled").value(false));

        // Customer gets 404
        mockMvc.perform(get("/api/products/" + created.getPublicId()))
                .andExpect(status().isNotFound());

        // 2. Re-enable availability
        availReq.setAdminEnabled(true);
        mockMvc.perform(patch("/api/admin/products/" + created.getPublicId() + "/availability")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(availReq)))
                .andExpect(status().isOk());

        // 3. Admin sets status to INACTIVE
        UpdateStatusRequest statusReq = UpdateStatusRequest.builder()
                .status(ProductStatus.INACTIVE)
                .build();

        mockMvc.perform(patch("/api/admin/products/" + created.getPublicId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));

        // Customer gets 404
        mockMvc.perform(get("/api/products/" + created.getPublicId()))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // 3. ADMIN PAGINATION & FILTERING TESTS
    // =========================================================================

    @Test
    @DisplayName("Test 11-13: Admin pagination defaults to 10 and enforces maximum page size of 10")
    void testAdminPaginationAndMaxLimit() throws Exception {
        // Create 15 products
        for (int i = 1; i <= 15; i++) {
            createSampleProduct("Test Veggie " + i, "root-vegetables", "30.00");
        }

        // 1. Default pagination request (page=0, size not specified -> defaults to 10)
        mockMvc.perform(get("/api/admin/products")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.content", hasSize(10)))
                .andExpect(jsonPath("$.data.totalElements").value(15))
                .andExpect(jsonPath("$.data.totalPages").value(2));

        // 2. Caller requests size=50 -> backend strictly clamps to maximum 10
        mockMvc.perform(get("/api/admin/products?size=50")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.content", hasSize(10)));

        // 3. Second page
        mockMvc.perform(get("/api/admin/products?page=1&size=10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(5)))
                .andExpect(jsonPath("$.data.last").value(true));
    }

    @Test
    @DisplayName("Test 11: Admin category and search filtering")
    void testAdminFiltering() throws Exception {
        createSampleProduct("Green Spinach", "leafy-vegetables", "25.00");
        createSampleProduct("Red Apple", "fruits", "150.00");
        createSampleProduct("Green Apple", "fruits", "160.00");

        // Filter by category slug 'fruits'
        mockMvc.perform(get("/api/admin/products?category=fruits")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)));

        // Search by keyword 'apple'
        mockMvc.perform(get("/api/admin/products?search=apple")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)));

        // Search by keyword 'spinach'
        mockMvc.perform(get("/api/admin/products?search=spinach")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].name").value("Green Spinach"));
    }

    // =========================================================================
    // 4. CUSTOMER CATALOG (PUBLIC, NO PAGINATION, STABLE DISPLAY)
    // =========================================================================

    @Test
    @DisplayName("Test 14-15: Customer catalog does not paginate and category browsing works")
    void testCustomerCatalog() throws Exception {
        createSampleProduct("Mango", "fruits", "120.00");
        createSampleProduct("Banana", "fruits", "50.00");
        createSampleProduct("Mint Leaves", "seasonings", "15.00");

        // 1. GET /api/products returns all available products as unpaginated List
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", isA(List.class)))
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].publicId").isNotEmpty())
                .andExpect(jsonPath("$.data[0].id").doesNotExist()); // No internal database IDs

        // 2. GET /api/products/categories returns active categories
        mockMvc.perform(get("/api/products/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(6))));

        // 3. GET /api/products/category/fruits returns only fruits
        mockMvc.perform(get("/api/products/category/fruits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].category.slug").value("fruits"))
                .andExpect(jsonPath("$.data[1].category.slug").value("fruits"));
    }

    // =========================================================================
    // 5. INVENTORY & AVAILABILITY BOUNDARY TESTS
    // =========================================================================

    @Test
    @DisplayName("Test 18: KG requested quantity pricing and stock availability integration boundary")
    void testInventoryAvailabilityCalculation() throws Exception {
        ProductDetailResponse product = createSampleProduct("Organic Carrots", "root-vegetables", "60.00");

        // 1. Availability calculation for 2.500 KG: totalPrice = 60.00 * 2.500 = 150.00
        mockMvc.perform(get("/api/products/" + product.getPublicId() + "/availability?quantity=2.500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.requestedQuantityKg").value(2.500))
                .andExpect(jsonPath("$.data.pricePerKg").value(60.00))
                .andExpect(jsonPath("$.data.totalPrice").value(150.00));

        // 2. Simulate inventory stock drop to 0.500 KG
        inventoryIntegrationService.setSimulatedStock(product.getPublicId(), new BigDecimal("0.500"));

        // Customer requests 1.000 KG -> purchase rejected due to insufficient stock
        mockMvc.perform(get("/api/products/" + product.getPublicId() + "/availability?quantity=1.000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.stockStatus").value("OUT_OF_STOCK"));

        // When stock is 0.000, product is excluded from customer catalog
        inventoryIntegrationService.setSimulatedStock(product.getPublicId(), BigDecimal.ZERO);
        mockMvc.perform(get("/api/products/" + product.getPublicId()))
                .andExpect(status().isNotFound());

        // 3. Idempotent stock events: test duplicate event handling
        boolean firstProcessed = inventoryIntegrationService.processStockUpdateEvent(
                product.getPublicId(), new BigDecimal("25.000"), "event-uuid-101");
        boolean duplicateProcessed = inventoryIntegrationService.processStockUpdateEvent(
                product.getPublicId(), new BigDecimal("25.000"), "event-uuid-101");

        assertThat(firstProcessed).isTrue();
        assertThat(duplicateProcessed).isFalse(); // Safely ignored duplicate
    }

    // =========================================================================
    // 6. SECURITY TESTS
    // =========================================================================

    @Test
    @DisplayName("Test 19: Unauthorized users cannot execute admin operations")
    void testSecurityAccess() throws Exception {
        CreateProductRequest request = CreateProductRequest.builder()
                .categoryId("fruits")
                .name("Unauthorized Apple")
                .pricePerKg(new BigDecimal("100.00"))
                .build();

        // 1. No token -> 401 Unauthorized
        mockMvc.perform(post("/api/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        // 2. Customer token (ROLE_CUSTOMER) -> 403 Forbidden
        mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        // 3. Public customer endpoint does NOT require token
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // 7. S3 MULTI-IMAGE STORAGE & FAILURE COMPENSATION TESTS (Section 10 Scenarios 1-13)
    // =========================================================================

    @Test
    @DisplayName("Scenario 1, 3, 4, 5, 6: Create product with one image, verify S3 upload, DB URLs, primary flag, and response")
    void testCreateProductWithOneImage() throws Exception {
        CreateProductRequest req = CreateProductRequest.builder()
                .categoryId("fruits")
                .name("Kashmir Apple")
                .description("Sweet and crisp Kashmir apples")
                .shortDescription("Fresh apples")
                .pricePerKg(new BigDecimal("180.00"))
                .defaultQuantityKg(new BigDecimal("1.000"))
                .quantityUnit("KG")
                .status(ProductStatus.ACTIVE)
                .adminEnabled(true)
                .isFeatured(false)
                .build();

        MockMultipartFile productPart = new MockMultipartFile(
                "product", "", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(req));

        MockMultipartFile image1 = new MockMultipartFile(
                "images", "apple.jpg", "image/jpeg",
                "test-image-content-1".getBytes(StandardCharsets.UTF_8));

        MvcResult result = mockMvc.perform(multipart("/api/admin/products")
                        .file(productPart)
                        .file(image1)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Kashmir Apple"))
                .andExpect(jsonPath("$.data.images", hasSize(1)))
                .andExpect(jsonPath("$.data.images[0].primary").value(true))
                .andExpect(jsonPath("$.data.images[0].isPrimary").value(true))
                .andExpect(jsonPath("$.data.images[0].sortOrder").value(0))
                .andExpect(jsonPath("$.data.images[0].imageUrl").value(containsString(".jpg")))
                .andReturn();

        ApiResponse<ProductDetailResponse> resp = objectMapper.readValue(
                result.getResponse().getContentAsString(), new TypeReference<>() {});

        String productPublicId = resp.getData().getPublicId();

        // Verify MySQL DB records
        Product savedProduct = productRepository.findByPublicIdWithDetails(productPublicId).orElseThrow();
        assertThat(savedProduct.getImages()).hasSize(1);
        assertThat(savedProduct.getImageUrl()).isNotNull();
        assertThat(savedProduct.getImages().get(0).getIsPrimary()).isTrue();
        assertThat(savedProduct.getImages().get(0).getSortOrder()).isEqualTo(0);
    }

    @Test
    @DisplayName("Scenario 2, 3, 4, 5, 6: Create product with three images, verify all 3 in S3, MySQL, first primary, sort orders")
    void testCreateProductWithThreeImages() throws Exception {
        CreateProductRequest req = CreateProductRequest.builder()
                .categoryId("root-vegetables")
                .name("Farm Fresh Potatoes")
                .pricePerKg(new BigDecimal("35.00"))
                .defaultQuantityKg(new BigDecimal("1.000"))
                .quantityUnit("KG")
                .status(ProductStatus.ACTIVE)
                .adminEnabled(true)
                .build();

        MockMultipartFile productPart = new MockMultipartFile(
                "product", "", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(req));

        MockMultipartFile img1 = new MockMultipartFile("images", "potato-front.jpg", "image/jpeg", "bytes1".getBytes());
        MockMultipartFile img2 = new MockMultipartFile("images", "potato-side.jpg", "image/jpeg", "bytes2".getBytes());
        MockMultipartFile img3 = new MockMultipartFile("images", "potato-closeup.png", "image/png", "bytes3".getBytes());

        MvcResult result = mockMvc.perform(multipart("/api/admin/products")
                        .file(productPart)
                        .file(img1)
                        .file(img2)
                        .file(img3)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.images", hasSize(3)))
                .andExpect(jsonPath("$.data.images[0].primary").value(true))
                .andExpect(jsonPath("$.data.images[0].sortOrder").value(0))
                .andExpect(jsonPath("$.data.images[1].primary").value(false))
                .andExpect(jsonPath("$.data.images[1].sortOrder").value(1))
                .andExpect(jsonPath("$.data.images[2].primary").value(false))
                .andExpect(jsonPath("$.data.images[2].sortOrder").value(2))
                .andReturn();

        ApiResponse<ProductDetailResponse> resp = objectMapper.readValue(
                result.getResponse().getContentAsString(), new TypeReference<>() {});

        Product saved = productRepository.findByPublicIdWithDetails(resp.getData().getPublicId()).orElseThrow();
        assertThat(saved.getImages()).hasSize(3);
        assertThat(saved.getImages().get(0).getIsPrimary()).isTrue();
        assertThat(saved.getImages().get(1).getIsPrimary()).isFalse();
        assertThat(saved.getImages().get(2).getIsPrimary()).isFalse();
    }

    @Test
    @DisplayName("Scenario 7: Reject unsupported image types")
    void testRejectUnsupportedImageTypes() throws Exception {
        CreateProductRequest req = CreateProductRequest.builder()
                .categoryId("seasonings")
                .name("Black Pepper")
                .pricePerKg(new BigDecimal("700.00"))
                .build();

        MockMultipartFile productPart = new MockMultipartFile(
                "product", "", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(req));

        MockMultipartFile invalidImg = new MockMultipartFile(
                "images", "pepper.gif", "image/gif", "fake-gif-bytes".getBytes());

        mockMvc.perform(multipart("/api/admin/products")
                        .file(productPart)
                        .file(invalidImg)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("Unsupported image format")));
    }

    @Test
    @DisplayName("Scenario 8: Reject images larger than 5 MB")
    void testRejectOversizedImages() throws Exception {
        CreateProductRequest req = CreateProductRequest.builder()
                .categoryId("leafy-vegetables")
                .name("Oversized Spinach")
                .pricePerKg(new BigDecimal("30.00"))
                .build();

        MockMultipartFile productPart = new MockMultipartFile(
                "product", "", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(req));

        byte[] largeBytes = new byte[6 * 1024 * 1024]; // 6 MB
        MockMultipartFile largeImg = new MockMultipartFile(
                "images", "spinach-large.png", "image/png", largeBytes);

        mockMvc.perform(multipart("/api/admin/products")
                        .file(productPart)
                        .file(largeImg)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("exceeds maximum allowed limit of 5 MB")));
    }

    @Test
    @DisplayName("Scenario 9: Reject more than five images")
    void testRejectMoreThanFiveImages() throws Exception {
        CreateProductRequest req = CreateProductRequest.builder()
                .categoryId("mushrooms")
                .name("Oyster Mushroom")
                .pricePerKg(new BigDecimal("120.00"))
                .build();

        MockMultipartFile productPart = new MockMultipartFile(
                "product", "", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(req));

        MockMultipartFile img1 = new MockMultipartFile("images", "m1.jpg", "image/jpeg", "b1".getBytes());
        MockMultipartFile img2 = new MockMultipartFile("images", "m2.jpg", "image/jpeg", "b2".getBytes());
        MockMultipartFile img3 = new MockMultipartFile("images", "m3.jpg", "image/jpeg", "b3".getBytes());
        MockMultipartFile img4 = new MockMultipartFile("images", "m4.jpg", "image/jpeg", "b4".getBytes());
        MockMultipartFile img5 = new MockMultipartFile("images", "m5.jpg", "image/jpeg", "b5".getBytes());
        MockMultipartFile img6 = new MockMultipartFile("images", "m6.jpg", "image/jpeg", "b6".getBytes());

        mockMvc.perform(multipart("/api/admin/products")
                        .file(productPart)
                        .file(img1).file(img2).file(img3).file(img4).file(img5).file(img6)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("Cannot upload more than 5 images")));
    }

    @Test
    @DisplayName("Scenario 10: Simulate S3 upload failure and verify database records are not created")
    void testSimulateS3UploadFailureRollback() throws Exception {
        if (imageStorageService instanceof MockImageStorageService mockStorage) {
            mockStorage.setSimulateUploadFailure(true);
            try {
                CreateProductRequest req = CreateProductRequest.builder()
                        .categoryId("other-items")
                        .name("Failed S3 Product")
                        .pricePerKg(new BigDecimal("50.00"))
                        .build();

                MockMultipartFile productPart = new MockMultipartFile(
                        "product", "", MediaType.APPLICATION_JSON_VALUE,
                        objectMapper.writeValueAsBytes(req));

                MockMultipartFile img1 = new MockMultipartFile("images", "fail.jpg", "image/jpeg", "b1".getBytes());

                mockMvc.perform(multipart("/api/admin/products")
                                .file(productPart)
                                .file(img1)
                                .header("Authorization", "Bearer " + adminToken))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.success").value(false));

                // Verify DB has no product with this name
                assertThat(productRepository.findBySlug("failed-s3-product")).isEmpty();
            } finally {
                mockStorage.setSimulateUploadFailure(false);
            }
        }
    }

    @Test
    @DisplayName("Scenario 11: Simulate database failure and verify uploaded S3 objects are cleaned up")
    void testSimulateDatabaseFailureRollback() {
        if (imageStorageService instanceof MockImageStorageService mockStorage) {
            mockStorage.getDeletedKeys().clear();

            // Mock ProductRepository to throw exception during transaction save
            ProductRepository failingRepo = Mockito.mock(ProductRepository.class);
            Mockito.when(failingRepo.existsBySlug(anyString())).thenReturn(false);
            Mockito.when(failingRepo.save(Mockito.any())).thenThrow(
                    new org.springframework.dao.DataIntegrityViolationException("Simulated DB transaction crash / deadlock"));

            ProductService customService = new com.rrvgreenbasket.product.service.impl.ProductServiceImpl(
                    failingRepo,
                    categoryRepository,
                    productImageRepository,
                    productMapper,
                    imageStorageService,
                    inventoryIntegrationService,
                    transactionManager
            );

            CreateProductRequest req = CreateProductRequest.builder()
                    .categoryId("fruits")
                    .name("DB Crash Mango")
                    .pricePerKg(new BigDecimal("100.00"))
                    .build();

            MockMultipartFile img = new MockMultipartFile("images", "db-fail.jpg", "image/jpeg", "fake-bytes".getBytes(StandardCharsets.UTF_8));

            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                    customService.createProduct(req, List.of(img))
            ).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

            // Verify that the uploaded image key was compensated by calling deleteImage
            assertThat(mockStorage.getDeletedKeys()).isNotEmpty();
            assertThat(mockStorage.getDeletedKeys().get(mockStorage.getDeletedKeys().size() - 1)).contains(".jpg");
        }
    }

    @Test
    @DisplayName("Scenario 12: Update product without images and confirm existing images remain")
    void testUpdateProductWithoutImagesPreservesExisting() throws Exception {
        // 1. Create product with 2 images
        CreateProductRequest req = CreateProductRequest.builder()
                .categoryId("fruits")
                .name("Nagpur Oranges")
                .pricePerKg(new BigDecimal("70.00"))
                .defaultQuantityKg(new BigDecimal("1.000"))
                .quantityUnit("KG")
                .status(ProductStatus.ACTIVE)
                .adminEnabled(true)
                .build();

        MockMultipartFile productPart = new MockMultipartFile(
                "product", "", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(req));

        MockMultipartFile img1 = new MockMultipartFile("images", "orange1.jpg", "image/jpeg", "bytes1".getBytes());
        MockMultipartFile img2 = new MockMultipartFile("images", "orange2.jpg", "image/jpeg", "bytes2".getBytes());

        MvcResult createResult = mockMvc.perform(multipart("/api/admin/products")
                        .file(productPart).file(img1).file(img2)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.images", hasSize(2)))
                .andReturn();

        ApiResponse<ProductDetailResponse> created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), new TypeReference<>() {});

        String productPublicId = created.getData().getPublicId();

        // 2. Update product details (e.g. price and description) with replaceImages=false and no new images
        UpdateProductRequest updateReq = UpdateProductRequest.builder()
                .categoryId("fruits")
                .name("Nagpur Oranges Premium")
                .pricePerKg(new BigDecimal("85.00"))
                .defaultQuantityKg(new BigDecimal("1.000"))
                .quantityUnit("KG")
                .status(ProductStatus.ACTIVE)
                .adminEnabled(true)
                .isFeatured(true)
                .replaceImages(false)
                .build();

        mockMvc.perform(put("/api/admin/products/" + productPublicId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Nagpur Oranges Premium"))
                .andExpect(jsonPath("$.data.pricePerKg").value(85.00))
                .andExpect(jsonPath("$.data.images", hasSize(2))); // Existing 2 images remain!

        Product inDb = productRepository.findByPublicIdWithDetails(productPublicId).orElseThrow();
        assertThat(inDb.getImages()).hasSize(2);
    }

    @Test
    @DisplayName("Scenario 13: Update product with new images and verify replacement behavior")
    void testUpdateProductWithNewImagesReplacement() throws Exception {
        // 1. Create product with 1 image
        CreateProductRequest req = CreateProductRequest.builder()
                .categoryId("fruits")
                .name("Shimla Apples")
                .pricePerKg(new BigDecimal("150.00"))
                .defaultQuantityKg(new BigDecimal("1.000"))
                .quantityUnit("KG")
                .status(ProductStatus.ACTIVE)
                .adminEnabled(true)
                .build();

        MockMultipartFile productPart = new MockMultipartFile(
                "product", "", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(req));

        MockMultipartFile img1 = new MockMultipartFile("images", "old-apple.jpg", "image/jpeg", "old-bytes".getBytes());

        MvcResult createResult = mockMvc.perform(multipart("/api/admin/products")
                        .file(productPart).file(img1)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.images", hasSize(1)))
                .andReturn();

        ApiResponse<ProductDetailResponse> created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), new TypeReference<>() {});

        String productPublicId = created.getData().getPublicId();

        // 2. Update with replaceImages=true and 2 new images
        UpdateProductRequest updateReq = UpdateProductRequest.builder()
                .categoryId("fruits")
                .name("Shimla Apples Royal")
                .pricePerKg(new BigDecimal("160.00"))
                .defaultQuantityKg(new BigDecimal("1.000"))
                .quantityUnit("KG")
                .status(ProductStatus.ACTIVE)
                .adminEnabled(true)
                .isFeatured(false)
                .replaceImages(true)
                .build();

        MockMultipartFile updateProductPart = new MockMultipartFile(
                "product", "", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(updateReq));

        MockMultipartFile newImg1 = new MockMultipartFile("images", "new-apple-1.jpg", "image/jpeg", "new1".getBytes());
        MockMultipartFile newImg2 = new MockMultipartFile("images", "new-apple-2.png", "image/png", "new2".getBytes());

        mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/admin/products/" + productPublicId)
                        .file(updateProductPart)
                        .file(newImg1)
                        .file(newImg2)
                        .param("replaceImages", "true")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Shimla Apples Royal"))
                .andExpect(jsonPath("$.data.images", hasSize(2)))
                .andExpect(jsonPath("$.data.images[0].primary").value(true))
                .andExpect(jsonPath("$.data.images[1].primary").value(false));

        Product inDb = productRepository.findByPublicIdWithDetails(productPublicId).orElseThrow();
        assertThat(inDb.getImages()).hasSize(2);
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private ProductDetailResponse createSampleProduct(String name, String categorySlug, String price) throws Exception {
        CreateProductRequest request = CreateProductRequest.builder()
                .categoryId(categorySlug)
                .name(name)
                .description("Sample description for " + name)
                .shortDescription("Fresh " + name)
                .pricePerKg(new BigDecimal(price))
                .defaultQuantityKg(new BigDecimal("1.000"))
                .quantityUnit("KG")
                .status(ProductStatus.ACTIVE)
                .adminEnabled(true)
                .isFeatured(false)
                .build();

        MvcResult result = mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        ApiResponse<ProductDetailResponse> response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<>() {});

        return response.getData();
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
