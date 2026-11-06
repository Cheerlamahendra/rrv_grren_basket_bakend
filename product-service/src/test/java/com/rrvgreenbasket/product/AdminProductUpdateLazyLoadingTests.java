package com.rrvgreenbasket.product;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.product.client.AuthServiceClient;
import com.rrvgreenbasket.product.dto.request.CreateProductRequest;
import com.rrvgreenbasket.product.dto.request.UpdateProductRequest;
import com.rrvgreenbasket.product.dto.response.ApiResponse;
import com.rrvgreenbasket.product.dto.response.ProductDetailResponse;
import com.rrvgreenbasket.product.entity.ProductCategory;
import com.rrvgreenbasket.product.enums.ProductStatus;
import com.rrvgreenbasket.product.repository.ProductCategoryRepository;
import com.rrvgreenbasket.product.repository.ProductImageRepository;
import com.rrvgreenbasket.product.repository.ProductRepository;
import com.rrvgreenbasket.product.service.ImageStorageService;
import com.rrvgreenbasket.product.service.ProductService;
import com.rrvgreenbasket.product.service.impl.MockImageStorageService;
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
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration,org.springframework.cloud.commons.util.InetUtilsAutoConfiguration"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AdminProductUpdateLazyLoadingTests {

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
    private ProductService productService;

    @Autowired
    private ImageStorageService imageStorageService;

    @MockBean
    private AuthServiceClient authServiceClient;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    private String adminToken;
    private ProductCategory fruitsCategory;
    private ProductCategory leafyCategory;

    @BeforeEach
    void setUp() {
        productImageRepository.deleteAll();
        productRepository.deleteAll();

        Mockito.when(authServiceClient.isSessionActive(anyString())).thenReturn(true);
        adminToken = generateJwtToken("admin-user-id", "admin@rrvgreenbasket.com", "ROLE_ADMIN");

        fruitsCategory = categoryRepository.findBySlug("fruits")
                .orElseThrow(() -> new IllegalStateException("Fruits category not found in test db"));
        leafyCategory = categoryRepository.findBySlug("leafy-vegetables")
                .orElseThrow(() -> new IllegalStateException("Leafy vegetables category not found in test db"));
    }

    @Test
    @DisplayName("Verify updateProduct does NOT throw Hibernate LazyInitializationException and category proxy initializes")
    void testUpdateProductWithoutCategoryChange_NoLazyInitializationException() throws Exception {
        // 1. Create a product first
        CreateProductRequest createReq = CreateProductRequest.builder()
                .categoryId(fruitsCategory.getPublicId())
                .name("Alphonso Mango")
                .slug("alphonso-mango")
                .description("Delicious ripe Alphonso mangoes")
                .shortDescription("Fresh mango")
                .pricePerKg(new BigDecimal("120.00"))
                .defaultQuantityKg(new BigDecimal("1.000"))
                .quantityUnit("KG")
                .status(ProductStatus.ACTIVE)
                .adminEnabled(true)
                .isFeatured(true)
                .build();

        ProductDetailResponse created = productService.createProduct(createReq);
        assertThat(created).isNotNull();
        assertThat(created.getCategory()).isNotNull();
        assertThat(created.getCategory().getPublicId()).isEqualTo(fruitsCategory.getPublicId());

        // 2. Perform Admin Update via JSON
        UpdateProductRequest updateReq = UpdateProductRequest.builder()
                .categoryId(fruitsCategory.getPublicId())
                .name("Premium Alphonso Mango")
                .slug("alphonso-mango")
                .description("Updated description")
                .shortDescription("Updated short")
                .pricePerKg(new BigDecimal("150.00"))
                .defaultQuantityKg(new BigDecimal("1.000"))
                .quantityUnit("KG")
                .status(ProductStatus.ACTIVE)
                .adminEnabled(true)
                .isFeatured(true)
                .build();

        MvcResult result = mockMvc.perform(put("/api/admin/products/" + created.getPublicId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Product updated successfully"))
                .andExpect(jsonPath("$.data.name").value("Premium Alphonso Mango"))
                .andExpect(jsonPath("$.data.pricePerKg").value(150.00))
                .andExpect(jsonPath("$.data.category.publicId").value(fruitsCategory.getPublicId()))
                .andExpect(jsonPath("$.data.category.name").value(fruitsCategory.getName()))
                .andReturn();

        ApiResponse<ProductDetailResponse> apiResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<>() {});

        assertThat(apiResponse.getData()).isNotNull();
        assertThat(apiResponse.getData().getCategory().getName()).isEqualTo("Fruits");
        assertThat(apiResponse.getData().getCategory().getPublicId()).isEqualTo(fruitsCategory.getPublicId());
    }

    @Test
    @DisplayName("Verify updateProduct with changed category initializes new category proxy cleanly")
    void testUpdateProductWithCategoryChange_NoLazyInitializationException() throws Exception {
        CreateProductRequest createReq = CreateProductRequest.builder()
                .categoryId(fruitsCategory.getPublicId())
                .name("Fresh Spinach Bundle")
                .pricePerKg(new BigDecimal("30.00"))
                .build();

        ProductDetailResponse created = productService.createProduct(createReq);

        // Update product to belong to Leafy Vegetables category
        UpdateProductRequest updateReq = UpdateProductRequest.builder()
                .categoryId(leafyCategory.getPublicId())
                .name("Fresh Spinach Bundle Premium")
                .pricePerKg(new BigDecimal("35.00"))
                .defaultQuantityKg(new BigDecimal("1.000"))
                .quantityUnit("KG")
                .status(ProductStatus.ACTIVE)
                .adminEnabled(true)
                .isFeatured(false)
                .build();

        mockMvc.perform(put("/api/admin/products/" + created.getPublicId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.category.publicId").value(leafyCategory.getPublicId()))
                .andExpect(jsonPath("$.data.category.name").value("Leafy Vegetables"))
                .andExpect(jsonPath("$.data.category.slug").value("leafy-vegetables"));
    }

    @Test
    @DisplayName("Verify updateProduct with multipart and replaceImages deletes old keys after commit")
    void testUpdateProductWithMultipartReplaceImages() throws Exception {
        if (imageStorageService instanceof MockImageStorageService mockStorage) {
            mockStorage.getDeletedKeys().clear();
        }

        MockMultipartFile initialImage = new MockMultipartFile(
                "images", "initial.jpg", "image/jpeg", "image-data-initial".getBytes(StandardCharsets.UTF_8));

        CreateProductRequest createReq = CreateProductRequest.builder()
                .categoryId(fruitsCategory.getPublicId())
                .name("Nagpur Orange")
                .pricePerKg(new BigDecimal("80.00"))
                .build();

        ProductDetailResponse created = productService.createProduct(createReq, List.of(initialImage));
        assertThat(created.getImages()).hasSize(1);
        String oldStorageKey = created.getImages().get(0).getStorageKey();

        // Now update multipart with replaceImages = true
        UpdateProductRequest updateReq = UpdateProductRequest.builder()
                .categoryId(fruitsCategory.getPublicId())
                .name("Nagpur Orange Sweet")
                .pricePerKg(new BigDecimal("85.00"))
                .defaultQuantityKg(new BigDecimal("1.000"))
                .quantityUnit("KG")
                .status(ProductStatus.ACTIVE)
                .adminEnabled(true)
                .isFeatured(true)
                .build();

        MockMultipartFile productPart = new MockMultipartFile(
                "product", "", "application/json", objectMapper.writeValueAsBytes(updateReq));
        MockMultipartFile newImage = new MockMultipartFile(
                "images", "new_orange.jpg", "image/jpeg", "image-data-new".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/admin/products/" + created.getPublicId())
                        .file(productPart)
                        .file(newImage)
                        .param("replaceImages", "true")
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Nagpur Orange Sweet"))
                .andExpect(jsonPath("$.data.category.name").value("Fruits"));

        if (imageStorageService instanceof MockImageStorageService mockStorage) {
            assertThat(mockStorage.getDeletedKeys()).contains(oldStorageKey);
        }
    }

    @Test
    @DisplayName("Verify updateProduct with non-existent product ID returns 404")
    void testUpdateProductNotFound() throws Exception {
        String randomId = UUID.randomUUID().toString();
        UpdateProductRequest updateReq = UpdateProductRequest.builder()
                .categoryId(fruitsCategory.getPublicId())
                .name("Non Existent")
                .pricePerKg(new BigDecimal("100.00"))
                .build();

        mockMvc.perform(put("/api/admin/products/" + randomId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Verify updateProduct with invalid category returns 404")
    void testUpdateProductInvalidCategory() throws Exception {
        CreateProductRequest createReq = CreateProductRequest.builder()
                .categoryId(fruitsCategory.getPublicId())
                .name("Red Apple")
                .pricePerKg(new BigDecimal("120.00"))
                .build();

        ProductDetailResponse created = productService.createProduct(createReq);

        UpdateProductRequest updateReq = UpdateProductRequest.builder()
                .categoryId("non-existent-category-id")
                .name("Red Apple Modified")
                .pricePerKg(new BigDecimal("130.00"))
                .build();

        mockMvc.perform(put("/api/admin/products/" + created.getPublicId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
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
