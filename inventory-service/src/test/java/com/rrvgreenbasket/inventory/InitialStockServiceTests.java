package com.rrvgreenbasket.inventory;

import com.rrvgreenbasket.inventory.client.ProductServiceClient;
import com.rrvgreenbasket.inventory.client.dto.ProductVerificationResponse;
import com.rrvgreenbasket.inventory.dto.request.CorrectInitialStockRequest;
import com.rrvgreenbasket.inventory.dto.request.InitialStockRequest;
import com.rrvgreenbasket.inventory.dto.request.SingleProductInitialStockRequest;
import com.rrvgreenbasket.inventory.dto.response.InventoryDetailResponse;
import com.rrvgreenbasket.inventory.entity.*;
import com.rrvgreenbasket.inventory.enums.*;
import com.rrvgreenbasket.inventory.exception.*;
import com.rrvgreenbasket.inventory.repository.*;
import com.rrvgreenbasket.inventory.security.UserPrincipal;
import com.rrvgreenbasket.inventory.service.InventoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration;
import org.springframework.cloud.netflix.eureka.EurekaDiscoveryClientConfiguration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

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
@ActiveProfiles("test")
class InitialStockServiceTests {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private StockReservationItemRepository reservationItemRepository;

    @Autowired
    private StockAlertRepository stockAlertRepository;

    @Autowired
    private InventoryEventOutboxRepository outboxRepository;

    @MockBean
    private ProductServiceClient productServiceClient;

    @BeforeEach
    void setUpSecurity() {
        UserPrincipal admin = UserPrincipal.create("admin-id-123", "admin@rrvgreenbasket.com", "ADMIN", "sess-1", "ACTIVE");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("1. Successfully initialize stock for single product with audit movement and outbox")
    void testInitializeSingleProductSuccess() {
        String prodId = UUID.randomUUID().toString();
        when(productServiceClient.getProductByPublicId(prodId)).thenReturn(
                ProductVerificationResponse.builder()
                        .publicId(prodId)
                        .name("Fresh Farm Tomatoes")
                        .quantityUnit("KG")
                        .status("ACTIVE")
                        .available(true)
                        .build()
        );

        SingleProductInitialStockRequest request = SingleProductInitialStockRequest.builder()
                .unitOfMeasure("KG")
                .initialQuantity(new BigDecimal("100.000"))
                .lowStockThreshold(new BigDecimal("10.000"))
                .reason("Initial warehouse stock")
                .notes("Verified batch 001")
                .build();

        InventoryDetailResponse response = inventoryService.initializeStock(prodId, request);

        assertThat(response).isNotNull();
        assertThat(response.getProductPublicId()).isEqualTo(prodId);
        assertThat(response.getPhysicalQuantity()).isEqualByComparingTo(new BigDecimal("100.000"));
        assertThat(response.getReservedQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getAvailableQuantity()).isEqualByComparingTo(new BigDecimal("100.000"));
        assertThat(response.getUnitOfMeasure()).isEqualTo("KG");
        assertThat(response.getInitialized()).isTrue();

        // Verify DB persistence
        Optional<InventoryItem> itemOpt = inventoryItemRepository.findByProductPublicId(prodId);
        assertThat(itemOpt).isPresent();
        assertThat(itemOpt.get().getInitialized()).isTrue();

        // Verify StockMovement audit record
        var movements = stockMovementRepository.findByProductPublicIdOrderByCreatedAtDesc(prodId, null);
        assertThat(movements.getContent()).hasSize(1);
        StockMovement m = movements.getContent().get(0);
        assertThat(m.getMovementType()).isEqualTo(MovementType.INITIAL_STOCK);
        assertThat(m.getPhysicalQuantityBefore()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(m.getPhysicalQuantityAfter()).isEqualByComparingTo(new BigDecimal("100.000"));
        assertThat(m.getAvailableQuantityBefore()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(m.getAvailableQuantityAfter()).isEqualByComparingTo(new BigDecimal("100.000"));
        assertThat(m.getPerformedBy()).isEqualTo("admin@rrvgreenbasket.com");

        // Verify Outbox event
        var pendingEvents = outboxRepository.findTop50ByEventStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING);
        assertThat(pendingEvents.stream().anyMatch(e -> e.getProductPublicId().equals(prodId) && "STOCK_INITIALIZED".equals(e.getEventType()))).isTrue();
    }

    @Test
    @DisplayName("2. Initialize with zero quantity creates OUT_OF_STOCK alert")
    void testInitializeWithZeroQuantityCreatesOutOfStockAlert() {
        String prodId = UUID.randomUUID().toString();
        when(productServiceClient.getProductByPublicId(prodId)).thenReturn(
                ProductVerificationResponse.builder()
                        .publicId(prodId)
                        .name("Spinach Bunch")
                        .quantityUnit("BUNCH")
                        .status("ACTIVE")
                        .build()
        );

        SingleProductInitialStockRequest request = SingleProductInitialStockRequest.builder()
                .unitOfMeasure("BUNCH")
                .initialQuantity(BigDecimal.ZERO)
                .lowStockThreshold(new BigDecimal("5.000"))
                .reason("Zero stock setup")
                .build();

        inventoryService.initializeStock(prodId, request);

        var alertOpt = stockAlertRepository.findByProductPublicIdAndAlertTypeAndAlertStatusIn(
                prodId, AlertType.OUT_OF_STOCK, List.of(AlertStatus.ACTIVE)
        );
        assertThat(alertOpt).isPresent();
        assertThat(alertOpt.get().getCurrentQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("3. Initialize with quantity <= threshold creates LOW_STOCK alert")
    void testInitializeWithLowStockQuantityCreatesLowStockAlert() {
        String prodId = UUID.randomUUID().toString();
        when(productServiceClient.getProductByPublicId(prodId)).thenReturn(
                ProductVerificationResponse.builder()
                        .publicId(prodId)
                        .name("Organic Apples")
                        .quantityUnit("KG")
                        .status("ACTIVE")
                        .build()
        );

        SingleProductInitialStockRequest request = SingleProductInitialStockRequest.builder()
                .unitOfMeasure("KG")
                .initialQuantity(new BigDecimal("8.000"))
                .lowStockThreshold(new BigDecimal("10.000"))
                .reason("Limited harvest")
                .build();

        inventoryService.initializeStock(prodId, request);

        var alertOpt = stockAlertRepository.findByProductPublicIdAndAlertTypeAndAlertStatusIn(
                prodId, AlertType.LOW_STOCK, List.of(AlertStatus.ACTIVE)
        );
        assertThat(alertOpt).isPresent();
        assertThat(alertOpt.get().getCurrentQuantity()).isEqualByComparingTo(new BigDecimal("8.000"));
    }

    @Test
    @DisplayName("4. Canonical unit normalization from PACKET to PACK succeeds")
    void testPacketNormalizesToCanonicalPack() {
        String prodId = UUID.randomUUID().toString();
        when(productServiceClient.getProductByPublicId(prodId)).thenReturn(
                ProductVerificationResponse.builder()
                        .publicId(prodId)
                        .name("Biscuits Pack")
                        .quantityUnit("PACK")
                        .status("ACTIVE")
                        .build()
        );

        SingleProductInitialStockRequest request = SingleProductInitialStockRequest.builder()
                .unitOfMeasure("PACKET") // Aliased unit passed by client
                .initialQuantity(new BigDecimal("50.000"))
                .lowStockThreshold(new BigDecimal("5.000"))
                .reason("Carton arrival")
                .build();

        InventoryDetailResponse resp = inventoryService.initializeStock(prodId, request);
        assertThat(resp.getUnitOfMeasure()).isEqualTo("PACK");

        InventoryItem item = inventoryItemRepository.findByProductPublicId(prodId).orElseThrow();
        assertThat(item.getUnitOfMeasure()).isEqualTo(UnitOfMeasure.PACK);
    }

    @Test
    @DisplayName("5. Bulk initialization of multiple products succeeds")
    void testInitializeMultipleProductsBulkSuccess() {
        String p1 = UUID.randomUUID().toString();
        String p2 = UUID.randomUUID().toString();

        when(productServiceClient.getProductByPublicId(p1)).thenReturn(
                ProductVerificationResponse.builder().publicId(p1).name("Carrot").quantityUnit("KG").status("ACTIVE").build()
        );
        when(productServiceClient.getProductByPublicId(p2)).thenReturn(
                ProductVerificationResponse.builder().publicId(p2).name("Eggs").quantityUnit("DOZEN").status("ACTIVE").build()
        );

        List<InitialStockRequest> bulkList = List.of(
                InitialStockRequest.builder().productPublicId(p1).unitOfMeasure("KG").initialPhysicalQuantity(new BigDecimal("20.000")).reason("Bulk 1").build(),
                InitialStockRequest.builder().productPublicId(p2).unitOfMeasure("DOZEN").initialPhysicalQuantity(new BigDecimal("15.000")).reason("Bulk 2").build()
        );

        List<InventoryDetailResponse> responses = inventoryService.initializeMultipleProducts(bulkList);
        assertThat(responses).hasSize(2);

        assertThat(inventoryItemRepository.findByProductPublicId(p1)).isPresent();
        assertThat(inventoryItemRepository.findByProductPublicId(p2)).isPresent();
    }

    @Test
    @DisplayName("6. Bulk initialization rejects duplicate product IDs in same request")
    void testBulkRejectsDuplicateProductIds() {
        String p1 = UUID.randomUUID().toString();
        List<InitialStockRequest> duplicates = List.of(
                InitialStockRequest.builder().productPublicId(p1).unitOfMeasure("KG").initialPhysicalQuantity(new BigDecimal("10.000")).reason("First").build(),
                InitialStockRequest.builder().productPublicId(p1).unitOfMeasure("KG").initialPhysicalQuantity(new BigDecimal("20.000")).reason("Second").build()
        );

        assertThatThrownBy(() -> inventoryService.initializeMultipleProducts(duplicates))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Duplicate product public ID in bulk initialization");
    }

    @Test
    @DisplayName("7. Bulk initialization rolls back completely if one item fails")
    void testBulkRollbackOnSingleFailure() {
        String p1 = UUID.randomUUID().toString();
        String p2 = UUID.randomUUID().toString(); // This one will fail unit matching

        when(productServiceClient.getProductByPublicId(p1)).thenReturn(
                ProductVerificationResponse.builder().publicId(p1).name("Cabbage").quantityUnit("PIECE").status("ACTIVE").build()
        );
        when(productServiceClient.getProductByPublicId(p2)).thenReturn(
                ProductVerificationResponse.builder().publicId(p2).name("Milk").quantityUnit("LITRE").status("ACTIVE").build()
        );

        List<InitialStockRequest> bulkList = List.of(
                InitialStockRequest.builder().productPublicId(p1).unitOfMeasure("PIECE").initialPhysicalQuantity(new BigDecimal("10.000")).reason("Item 1").build(),
                InitialStockRequest.builder().productPublicId(p2).unitOfMeasure("KG").initialPhysicalQuantity(new BigDecimal("10.000")).reason("Item 2 mismatch").build()
        );

        assertThatThrownBy(() -> inventoryService.initializeMultipleProducts(bulkList))
                .isInstanceOf(UnitMismatchException.class);

        // Transaction rollback verification: neither product 1 nor product 2 must exist in inventory
        assertThat(inventoryItemRepository.findByProductPublicId(p1)).isEmpty();
        assertThat(inventoryItemRepository.findByProductPublicId(p2)).isEmpty();
    }

    @Test
    @DisplayName("8. Product not found in Product Service throws 404 ProductNotFoundException")
    void testProductNotFoundThrows() {
        String missingProdId = UUID.randomUUID().toString();
        when(productServiceClient.getProductByPublicId(missingProdId))
                .thenThrow(new ProductNotFoundException("Product not found with public ID: " + missingProdId));

        SingleProductInitialStockRequest request = SingleProductInitialStockRequest.builder()
                .unitOfMeasure("KG")
                .initialQuantity(new BigDecimal("10.000"))
                .reason("Init")
                .build();

        assertThatThrownBy(() -> inventoryService.initializeStock(missingProdId, request))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    @DisplayName("9. Product Service unavailable throws 503 ProductServiceUnavailableException")
    void testProductServiceUnavailableThrows() {
        String prodId = UUID.randomUUID().toString();
        when(productServiceClient.getProductByPublicId(prodId))
                .thenThrow(new ProductServiceUnavailableException("Product Service is currently unavailable."));

        SingleProductInitialStockRequest request = SingleProductInitialStockRequest.builder()
                .unitOfMeasure("KG")
                .initialQuantity(new BigDecimal("10.000"))
                .reason("Init")
                .build();

        assertThatThrownBy(() -> inventoryService.initializeStock(prodId, request))
                .isInstanceOf(ProductServiceUnavailableException.class);
    }

    @Test
    @DisplayName("10. Unit mismatch throws 400 UnitMismatchException")
    void testUnitMismatchThrows() {
        String prodId = UUID.randomUUID().toString();
        when(productServiceClient.getProductByPublicId(prodId)).thenReturn(
                ProductVerificationResponse.builder().publicId(prodId).name("Potatoes").quantityUnit("KG").status("ACTIVE").build()
        );

        SingleProductInitialStockRequest request = SingleProductInitialStockRequest.builder()
                .unitOfMeasure("LITRE") // Invalid unit for potatoes
                .initialQuantity(new BigDecimal("10.000"))
                .reason("Init")
                .build();

        assertThatThrownBy(() -> inventoryService.initializeStock(prodId, request))
                .isInstanceOf(UnitMismatchException.class)
                .hasMessageContaining("does not match product configured unit");
    }

    @Test
    @DisplayName("11. Negative initial quantity throws 400 InvalidRequestException")
    void testNegativeQuantityThrows() {
        String prodId = UUID.randomUUID().toString();
        SingleProductInitialStockRequest request = SingleProductInitialStockRequest.builder()
                .unitOfMeasure("KG")
                .initialQuantity(new BigDecimal("-1.000"))
                .reason("Init")
                .build();

        assertThatThrownBy(() -> inventoryService.initializeStock(prodId, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("cannot be negative");
    }

    @Test
    @DisplayName("12. Initializing already initialized product throws 409 InventoryAlreadyInitializedException")
    void testAlreadyInitializedThrows() {
        String prodId = UUID.randomUUID().toString();
        when(productServiceClient.getProductByPublicId(prodId)).thenReturn(
                ProductVerificationResponse.builder().publicId(prodId).name("Rice").quantityUnit("KG").status("ACTIVE").build()
        );

        SingleProductInitialStockRequest request = SingleProductInitialStockRequest.builder()
                .unitOfMeasure("KG")
                .initialQuantity(new BigDecimal("100.000"))
                .reason("Init first")
                .build();

        inventoryService.initializeStock(prodId, request);

        // Second call must fail
        assertThatThrownBy(() -> inventoryService.initializeStock(prodId, request))
                .isInstanceOf(InventoryAlreadyInitializedException.class)
                .hasMessageContaining("already been initialized");
    }

    @Test
    @DisplayName("13. Initial stock correction succeeds when no subsequent activity exists")
    void testCorrectInitialStockSuccess() {
        String prodId = UUID.randomUUID().toString();
        when(productServiceClient.getProductByPublicId(prodId)).thenReturn(
                ProductVerificationResponse.builder().publicId(prodId).name("Onions").quantityUnit("KG").status("ACTIVE").build()
        );

        SingleProductInitialStockRequest req = SingleProductInitialStockRequest.builder()
                .unitOfMeasure("KG")
                .initialQuantity(new BigDecimal("50.000"))
                .reason("Initial entry")
                .build();
        inventoryService.initializeStock(prodId, req);

        CorrectInitialStockRequest correctReq = CorrectInitialStockRequest.builder()
                .productPublicId(prodId)
                .correctedQuantity(new BigDecimal("60.000"))
                .reason("Typo fix in physical count sheet")
                .notes("Recounted 60 kg")
                .build();

        InventoryDetailResponse corrected = inventoryService.correctInitialStock(prodId, correctReq);

        assertThat(corrected.getPhysicalQuantity()).isEqualByComparingTo(new BigDecimal("60.000"));
        assertThat(corrected.getAvailableQuantity()).isEqualByComparingTo(new BigDecimal("60.000"));

        var movements = stockMovementRepository.findByProductPublicIdOrderByCreatedAtDesc(prodId, null);
        assertThat(movements.getContent()).hasSize(2);
        assertThat(movements.getContent().get(0).getMovementType()).isEqualTo(MovementType.ADJUSTMENT);
        assertThat(movements.getContent().get(0).getPhysicalQuantityAfter()).isEqualByComparingTo(new BigDecimal("60.000"));
    }
}
