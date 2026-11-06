package com.rrvgreenbasket.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.inventory.client.ProductServiceClient;
import com.rrvgreenbasket.inventory.client.dto.ProductVerificationResponse;
import com.rrvgreenbasket.inventory.dto.request.ReservationItemDto;
import com.rrvgreenbasket.inventory.dto.request.SingleProductInitialStockRequest;
import com.rrvgreenbasket.inventory.dto.request.StockInRequest;
import com.rrvgreenbasket.inventory.dto.request.StockReservationRequestDto;
import com.rrvgreenbasket.inventory.publisher.ProductStockEventPublisher;
import com.rrvgreenbasket.inventory.service.InventoryService;
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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class InternalAndCustomerInventoryControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InventoryService inventoryService;

    @MockBean
    private ProductServiceClient productServiceClient;

    @MockBean
    private ProductStockEventPublisher eventPublisher;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.security.internal-service-secret}")
    private String internalSecret;

    private SecretKey jwtKey;
    private SecretKey internalHmacKey;

    @BeforeEach
    void setUp() {
        this.jwtKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        byte[] keyBytes = internalSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        this.internalHmacKey = Keys.hmacShaKeyFor(keyBytes);
    }

    private String generateToken(String role) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject("user-uuid-" + UUID.randomUUID())
                .claim("email", "test-" + role.toLowerCase() + "@rrvgreenbasket.com")
                .claim("role", role)
                .claim("sid", "session-" + UUID.randomUUID())
                .claim("status", "ACTIVE")
                .issuer("rrv-gateway")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(jwtKey)
                .compact();
    }

    private String generateGatewayAssertion(String role) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject("order-service")
                .issuer("rrv-gateway-internal")
                .audience().add("rrv-backend-internal").and()
                .claim("role", role)
                .claim("sid", "session-s2s")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(internalHmacKey, Jwts.SIG.HS256)
                .compact();
    }

    private String initializeTestProduct(String unit, BigDecimal qty) {
        String prodId = UUID.randomUUID().toString();
        when(productServiceClient.getProductByPublicId(prodId)).thenReturn(
                ProductVerificationResponse.builder()
                        .publicId(prodId)
                        .name("Test Unit Product")
                        .quantityUnit(unit)
                        .status("ACTIVE")
                        .available(true)
                        .build()
        );

        inventoryService.initializeStock(prodId, SingleProductInitialStockRequest.builder()
                .unitOfMeasure(unit)
                .initialQuantity(qty)
                .lowStockThreshold(new BigDecimal("5.000"))
                .build());

        return prodId;
    }

    @Test
    @DisplayName("Public Customer API: GET /api/inventory/products/{id}/availability accessible without authentication")
    void testCustomerAvailabilityPublicAccess() throws Exception {
        String prodId = initializeTestProduct("KG", new BigDecimal("50.000"));

        mockMvc.perform(get("/api/inventory/products/" + prodId + "/availability?quantity=2.000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.productPublicId").value(prodId))
                .andExpect(jsonPath("$.data.inStock").value(true))
                .andExpect(jsonPath("$.data.sufficient").value(true))
                .andExpect(jsonPath("$.data.availableQuantity").value(50.0));
    }

    @Test
    @DisplayName("Internal API: Reservation and confirmation with Gateway Assertion S2S authentication")
    void testInternalReservationLifecycleWithGatewayAssertion() throws Exception {
        String prodId = initializeTestProduct("KG", new BigDecimal("40.000"));
        String orderId = UUID.randomUUID().toString();
        String assertion = generateGatewayAssertion("ROLE_INTERNAL_SERVICE");

        StockReservationRequestDto request = StockReservationRequestDto.builder()
                .orderPublicId(orderId)
                .idempotencyKey("checkout-order-" + UUID.randomUUID())
                .ttlMinutes(15)
                .items(List.of(ReservationItemDto.builder().productPublicId(prodId).quantity(new BigDecimal("5.000")).unitOfMeasure("KG").build()))
                .build();

        // 1. Create reservation
        String resContent = mockMvc.perform(post("/api/internal/inventory/reservations")
                        .header("X-Gateway-Assertion", assertion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();

        String resPublicId = objectMapper.readTree(resContent).get("data").get("publicId").asText();

        // 2. Confirm reservation
        String confirmAssertion = generateGatewayAssertion("ROLE_INTERNAL_SERVICE");
        mockMvc.perform(post("/api/internal/inventory/reservations/" + resPublicId + "/confirm")
                        .header("X-Gateway-Assertion", confirmAssertion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("Admin API: Stock-in endpoint allows ADMIN and denies CUSTOMER")
    void testAdminStockInSecurity() throws Exception {
        String prodId = initializeTestProduct("KG", new BigDecimal("10.000"));
        String adminToken = generateToken("ADMIN");
        String customerToken = generateToken("CUSTOMER");

        StockInRequest request = StockInRequest.builder()
                .productPublicId(prodId)
                .quantity(new BigDecimal("5.000"))
                .unitOfMeasure("KG")
                .reason("Test replenishment")
                .build();

        // Customer token rejected (403)
        mockMvc.perform(post("/api/admin/inventory/stock-in")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        // Admin token accepted (200)
        mockMvc.perform(post("/api/admin/inventory/stock-in")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.physicalQuantity").value(15.0));
    }
}
