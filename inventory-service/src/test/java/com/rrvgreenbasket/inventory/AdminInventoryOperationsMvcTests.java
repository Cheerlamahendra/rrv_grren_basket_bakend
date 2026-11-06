package com.rrvgreenbasket.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.inventory.client.ProductServiceClient;
import com.rrvgreenbasket.inventory.client.dto.ProductVerificationResponse;
import com.rrvgreenbasket.inventory.dto.request.*;
import com.rrvgreenbasket.inventory.enums.StockStatus;
import com.rrvgreenbasket.inventory.enums.UnitOfMeasure;
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
import java.util.UUID;

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
class AdminInventoryOperationsMvcTests {

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

    private SecretKey jwtKey;
    private String adminToken;

    @BeforeEach
    void setUp() {
        this.jwtKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.adminToken = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject("admin-uuid-mvc")
                .claim("email", "admin-mvc@rrvgreenbasket.com")
                .claim("role", "ADMIN")
                .claim("sid", "session-mvc")
                .claim("status", "ACTIVE")
                .issuer("rrv-gateway")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(jwtKey)
                .compact();
    }

    private String initializeTestProduct(String unit, BigDecimal qty) {
        String prodId = UUID.randomUUID().toString();
        when(productServiceClient.getProductByPublicId(prodId)).thenReturn(
                ProductVerificationResponse.builder()
                        .publicId(prodId)
                        .name("MVC Product " + prodId)
                        .quantityUnit(unit)
                        .status("ACTIVE")
                        .available(true)
                        .build()
        );

        inventoryService.initializeStock(prodId, SingleProductInitialStockRequest.builder()
                .unitOfMeasure(unit)
                .initialQuantity(qty)
                .lowStockThreshold(new BigDecimal("10.000"))
                .build());

        return prodId;
    }

    @Test
    @DisplayName("Admin MVC: Stock-in, stock-out, and adjustments endpoints work through MockMvc")
    void testStockOperationsEndpoints() throws Exception {
        String prodId = initializeTestProduct("KG", new BigDecimal("100.000"));

        // 1. Stock-In
        StockInRequest inReq = StockInRequest.builder()
                .productPublicId(prodId)
                .quantity(new BigDecimal("20.000"))
                .unitOfMeasure("KG")
                .reason("Warehouse intake")
                .build();

        mockMvc.perform(post("/api/admin/inventory/stock-in")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.physicalQuantity").value(120.0))
                .andExpect(jsonPath("$.data.availableQuantity").value(120.0));

        // 2. Stock-Out
        StockOutRequest outReq = StockOutRequest.builder()
                .productPublicId(prodId)
                .quantity(new BigDecimal("10.000"))
                .unitOfMeasure("KG")
                .reason("Damaged")
                .build();

        mockMvc.perform(post("/api/admin/inventory/stock-out")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(outReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.physicalQuantity").value(110.0));

        // 3. Stock Adjustment
        StockAdjustmentRequest adjReq = StockAdjustmentRequest.builder()
                .productPublicId(prodId)
                .newPhysicalQuantity(new BigDecimal("115.000"))
                .unitOfMeasure("KG")
                .reason("Cycle recount adjustment")
                .build();

        mockMvc.perform(post("/api/admin/inventory/adjustments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adjReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.physicalQuantity").value(115.0));
    }

    @Test
    @DisplayName("Admin MVC: List inventory items, movements audit, and alerts")
    void testQueryEndpoints() throws Exception {
        String prodId = initializeTestProduct("KG", new BigDecimal("40.000"));

        // 1. Inventory Listing
        mockMvc.perform(get("/api/admin/inventory")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("productPublicId", prodId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].productPublicId").value(prodId));

        // 2. Stock Movements
        mockMvc.perform(get("/api/admin/inventory/movements")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("productPublicId", prodId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());

        // 3. Stock Alerts
        mockMvc.perform(get("/api/admin/inventory/alerts")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("Admin MVC: Update threshold updates value and recalculates alerts")
    void testUpdateThresholdEndpoint() throws Exception {
        String prodId = initializeTestProduct("KG", new BigDecimal("30.000"));

        UpdateThresholdRequest req = UpdateThresholdRequest.builder()
                .lowStockThreshold(new BigDecimal("18.000"))
                .build();

        mockMvc.perform(patch("/api/admin/inventory/" + prodId + "/threshold")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.lowStockThreshold").value(18.0));
    }
}
