package com.rrvgreenbasket.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.inventory.client.ProductServiceClient;
import com.rrvgreenbasket.inventory.client.dto.ProductVerificationResponse;
import com.rrvgreenbasket.inventory.dto.request.BulkInitialStockRequest;
import com.rrvgreenbasket.inventory.dto.request.InitialStockRequest;
import com.rrvgreenbasket.inventory.dto.request.SingleProductInitialStockRequest;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration;
import org.springframework.cloud.netflix.eureka.EurekaDiscoveryClientConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.cloud.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=false"
})
@EnableAutoConfiguration(exclude = {
        EurekaClientAutoConfiguration.class,
        EurekaDiscoveryClientConfiguration.class
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminInventoryControllerSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductServiceClient productServiceClient;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    private SecretKey key;

    @BeforeEach
    void setUp() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    private String createToken(String userId, String email, String role) {
        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .claim("role", role)
                .claim("sid", "session-" + UUID.randomUUID())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(key)
                .compact();
    }

    @Test
    @DisplayName("Admin endpoint returns 401 when request has no authentication token")
    void testUnauthenticatedRequestReturns401() throws Exception {
        String prodId = UUID.randomUUID().toString();
        SingleProductInitialStockRequest request = SingleProductInitialStockRequest.builder()
                .unitOfMeasure("KG")
                .initialQuantity(new BigDecimal("100.000"))
                .reason("Test")
                .build();

        mockMvc.perform(put("/api/admin/inventory/" + prodId + "/initialize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Admin endpoint returns 403 when authenticated user is CUSTOMER")
    void testCustomerRoleReturns403Forbidden() throws Exception {
        String prodId = UUID.randomUUID().toString();
        String customerToken = createToken("cust-01", "customer@example.com", "ROLE_CUSTOMER");

        SingleProductInitialStockRequest request = SingleProductInitialStockRequest.builder()
                .unitOfMeasure("KG")
                .initialQuantity(new BigDecimal("100.000"))
                .reason("Test")
                .build();

        mockMvc.perform(put("/api/admin/inventory/" + prodId + "/initialize")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Admin endpoint returns 200 OK when authenticated user is ADMIN")
    void testAdminRoleReturns200Success() throws Exception {
        String prodId = UUID.randomUUID().toString();
        String adminToken = createToken("admin-01", "admin@rrvgreenbasket.com", "ROLE_ADMIN");

        when(productServiceClient.getProductByPublicId(prodId)).thenReturn(
                ProductVerificationResponse.builder()
                        .publicId(prodId)
                        .name("Fresh Mangoes")
                        .quantityUnit("KG")
                        .status("ACTIVE")
                        .build()
        );

        SingleProductInitialStockRequest request = SingleProductInitialStockRequest.builder()
                .unitOfMeasure("KG")
                .initialQuantity(new BigDecimal("100.000"))
                .reason("Fresh stock arrival")
                .build();

        mockMvc.perform(put("/api/admin/inventory/" + prodId + "/initialize")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.productPublicId").value(prodId))
                .andExpect(jsonPath("$.data.availableQuantity").value(100.000));
    }

    @Test
    @DisplayName("Bulk initialize endpoint returns 200 OK when called with ADMIN authority")
    void testBulkInitializeWithAdminAuthority() throws Exception {
        String adminToken = createToken("admin-01", "admin@rrvgreenbasket.com", "ADMIN");
        String p1 = UUID.randomUUID().toString();
        String p2 = UUID.randomUUID().toString();

        when(productServiceClient.getProductByPublicId(p1)).thenReturn(
                ProductVerificationResponse.builder().publicId(p1).name("Banana").quantityUnit("DOZEN").status("ACTIVE").build()
        );
        when(productServiceClient.getProductByPublicId(p2)).thenReturn(
                ProductVerificationResponse.builder().publicId(p2).name("Papaya").quantityUnit("PIECE").status("ACTIVE").build()
        );

        BulkInitialStockRequest request = BulkInitialStockRequest.builder()
                .items(List.of(
                        InitialStockRequest.builder().productPublicId(p1).unitOfMeasure("DOZEN").initialPhysicalQuantity(new BigDecimal("20.000")).reason("R1").build(),
                        InitialStockRequest.builder().productPublicId(p2).unitOfMeasure("PIECE").initialPhysicalQuantity(new BigDecimal("15.000")).reason("R2").build()
                ))
                .build();

        mockMvc.perform(post("/api/admin/inventory/initialize")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("Admin endpoint returns 400 when productPublicId is not a valid UUID")
    void testInvalidUuidReturns400() throws Exception {
        String adminToken = createToken("admin-01", "admin@rrvgreenbasket.com", "ROLE_ADMIN");

        SingleProductInitialStockRequest request = SingleProductInitialStockRequest.builder()
                .unitOfMeasure("KG")
                .initialQuantity(new BigDecimal("10.000"))
                .reason("Bad ID")
                .build();

        mockMvc.perform(put("/api/admin/inventory/not-a-valid-uuid/initialize")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
