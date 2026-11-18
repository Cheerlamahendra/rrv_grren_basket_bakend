package com.rrvgreenbasket.cart.service;

import com.rrvgreenbasket.cart.client.InventoryServiceClient;
import com.rrvgreenbasket.cart.client.ProductServiceClient;
import com.rrvgreenbasket.cart.config.CartExpirationProperties;
import com.rrvgreenbasket.cart.dto.client.ProductAvailabilityResponse;
import com.rrvgreenbasket.cart.dto.client.ProductDetailResponse;
import com.rrvgreenbasket.cart.dto.client.StockAvailabilityResponse;
import com.rrvgreenbasket.cart.dto.request.AddCartItemRequest;
import com.rrvgreenbasket.cart.dto.request.UpdateCartItemRequest;
import com.rrvgreenbasket.cart.dto.response.*;
import com.rrvgreenbasket.cart.entity.Cart;
import com.rrvgreenbasket.cart.entity.CartItem;
import com.rrvgreenbasket.cart.entity.CartStatus;
import com.rrvgreenbasket.cart.exception.*;
import com.rrvgreenbasket.cart.mapper.CartResponseMapper;
import com.rrvgreenbasket.cart.repository.CartItemRepository;
import com.rrvgreenbasket.cart.repository.CartRepository;
import com.rrvgreenbasket.cart.service.impl.CartServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartServiceImpl Unit Tests")
class CartServiceTests {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductServiceClient productServiceClient;

    @Mock
    private InventoryServiceClient inventoryServiceClient;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private CartResponseMapper cartResponseMapper;
    private CartExpirationProperties expirationProperties;
    private CartServiceImpl cartService;

    private final String customerId = "customer-uuid-001";
    private final String productPublicId = "prod-uuid-111";

    @BeforeEach
    void setUp() {
        cartResponseMapper = new CartResponseMapper();
        expirationProperties = new CartExpirationProperties();
        expirationProperties.setDurationDays(7);
        expirationProperties.setBatchSize(100);

        lenient().when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);

        cartService = new CartServiceImpl(
                cartRepository,
                cartItemRepository,
                cartResponseMapper,
                productServiceClient,
                inventoryServiceClient,
                expirationProperties,
                transactionManager
        );
    }

    private ProductDetailResponse createProductDetail(String publicId, String status, boolean adminEnabled, boolean available, BigDecimal price, String unit) {
        return new ProductDetailResponse(
                publicId,
                "Fresh Spinach",
                price,
                BigDecimal.ONE,
                unit,
                price,
                "http://example.com/spinach.png",
                status,
                adminEnabled,
                false,
                available,
                "IN_STOCK"
        );
    }

    private StockAvailabilityResponse createStockResponse(String publicId, BigDecimal availableQty, boolean sufficient, boolean inStock, String unit) {
        return new StockAvailabilityResponse(
                publicId,
                unit,
                availableQty,
                BigDecimal.ZERO,
                availableQty,
                BigDecimal.ONE,
                sufficient,
                inStock,
                inStock ? "IN_STOCK" : "OUT_OF_STOCK",
                false
        );
    }

    @Test
    @DisplayName("getMyCart returns existing active cart")
    void testGetMyCart_ExistingCart() {
        Cart cart = new Cart();
        cart.setId(1L);
        cart.setPublicId("cart-uuid");
        cart.setCustomerPublicId(customerId);
        cart.setStatus(CartStatus.ACTIVE);
        cart.setCurrency("INR");

        when(cartRepository.findByCustomerPublicIdAndStatus(customerId, CartStatus.ACTIVE))
                .thenReturn(Optional.of(cart));

        CartResponse response = cartService.getMyCart(customerId);

        assertNotNull(response);
        assertEquals("cart-uuid", response.getCartPublicId());
        assertEquals(CartStatus.ACTIVE, response.getStatus());
        assertEquals(0, response.getTotalItems());
    }

    @Test
    @DisplayName("getMyCart creates new active cart with expiration if none exists")
    void testGetMyCart_CreatesNewCart() {
        when(cartRepository.findByCustomerPublicIdAndStatus(customerId, CartStatus.ACTIVE))
                .thenReturn(Optional.empty());

        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> {
            Cart saved = inv.getArgument(0);
            saved.setId(10L);
            saved.setPublicId("new-cart-uuid");
            return saved;
        });

        CartResponse response = cartService.getMyCart(customerId);

        assertNotNull(response);
        assertEquals("new-cart-uuid", response.getCartPublicId());
        verify(cartRepository, times(1)).save(any(Cart.class));
    }

    @Test
    @DisplayName("addItem successfully adds item to active cart")
    void testAddItem_Success() {
        AddCartItemRequest request = AddCartItemRequest.builder()
                .productPublicId(productPublicId)
                .quantity(new BigDecimal("2.000"))
                .unitOfMeasure("KG")
                .build();

        ProductDetailResponse product = createProductDetail(productPublicId, "ACTIVE", true, true, new BigDecimal("35.00"), "KG");
        when(productServiceClient.getProductByPublicId(productPublicId)).thenReturn(product);
        when(productServiceClient.checkProductAvailability(eq(productPublicId), any()))
                .thenReturn(new ProductAvailabilityResponse(productPublicId, "Fresh Spinach", true, true, "ACTIVE", null, new BigDecimal("2.000"), new BigDecimal("35.00"), new BigDecimal("70.00"), null));

        StockAvailabilityResponse stock = createStockResponse(productPublicId, new BigDecimal("50.000"), true, true, "KG");
        when(inventoryServiceClient.checkStockAvailability(eq(productPublicId), any())).thenReturn(stock);

        Cart cart = new Cart();
        cart.setId(1L);
        cart.setPublicId("cart-1");
        cart.setCustomerPublicId(customerId);
        cart.setStatus(CartStatus.ACTIVE);

        when(cartRepository.findByCustomerPublicIdAndStatus(customerId, CartStatus.ACTIVE))
                .thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductPublicIdAndUnitOfMeasure(eq(1L), eq(productPublicId), eq("KG")))
                .thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenReturn(cart);

        CartResponse response = cartService.addItem(customerId, request);

        assertNotNull(response);
        assertEquals(1, response.getTotalItems());
        assertEquals(new BigDecimal("70.00"), response.getSubtotal()); // 2.0 * 35.00
    }

    @Test
    @DisplayName("addItem merges quantity for duplicate product and unit")
    void testAddItem_MergeDuplicate() {
        AddCartItemRequest request = AddCartItemRequest.builder()
                .productPublicId(productPublicId)
                .quantity(new BigDecimal("1.500"))
                .unitOfMeasure("KG")
                .build();

        ProductDetailResponse product = createProductDetail(productPublicId, "ACTIVE", true, true, new BigDecimal("40.00"), "KG");
        when(productServiceClient.getProductByPublicId(productPublicId)).thenReturn(product);
        when(productServiceClient.checkProductAvailability(eq(productPublicId), any()))
                .thenReturn(new ProductAvailabilityResponse(productPublicId, "Fresh Spinach", true, true, "ACTIVE", null, new BigDecimal("1.500"), new BigDecimal("40.00"), new BigDecimal("60.00"), null));

        StockAvailabilityResponse stock = createStockResponse(productPublicId, new BigDecimal("50.000"), true, true, "KG");
        when(inventoryServiceClient.checkStockAvailability(eq(productPublicId), any())).thenReturn(stock);

        Cart cart = new Cart();
        cart.setId(1L);
        cart.setPublicId("cart-1");
        cart.setCustomerPublicId(customerId);
        cart.setStatus(CartStatus.ACTIVE);

        CartItem existingItem = new CartItem();
        existingItem.setId(101L);
        existingItem.setPublicId("item-101");
        existingItem.setProductPublicId(productPublicId);
        existingItem.setProductNameSnapshot("Fresh Spinach");
        existingItem.setQuantity(new BigDecimal("2.000"));
        existingItem.setUnitOfMeasure("KG");
        existingItem.setPricePerKgSnapshot(new BigDecimal("40.00"));
        cart.addItem(existingItem);

        when(cartRepository.findByCustomerPublicIdAndStatus(customerId, CartStatus.ACTIVE))
                .thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductPublicIdAndUnitOfMeasure(eq(1L), eq(productPublicId), eq("KG")))
                .thenReturn(Optional.of(existingItem));
        when(cartRepository.save(any(Cart.class))).thenReturn(cart);

        CartResponse response = cartService.addItem(customerId, request);

        assertNotNull(response);
        assertEquals(1, response.getTotalItems(), "Merged items must remain 1 entry");
        assertEquals(new BigDecimal("3.500"), response.getItems().get(0).getQuantity()); // 2.0 + 1.5
        assertEquals(new BigDecimal("140.00"), response.getSubtotal()); // 3.5 * 40.00
    }

    @Test
    @DisplayName("addItem rejects inactive product")
    void testAddItem_RejectsInactiveProduct() {
        AddCartItemRequest request = AddCartItemRequest.builder()
                .productPublicId(productPublicId)
                .quantity(new BigDecimal("1.000"))
                .build();

        ProductDetailResponse inactiveProduct = createProductDetail(productPublicId, "INACTIVE", true, true, new BigDecimal("40.00"), "KG");
        when(productServiceClient.getProductByPublicId(productPublicId)).thenReturn(inactiveProduct);

        assertThrows(ProductUnavailableException.class, () -> cartService.addItem(customerId, request));
    }

    @Test
    @DisplayName("addItem rejects disabled product")
    void testAddItem_RejectsDisabledProduct() {
        AddCartItemRequest request = AddCartItemRequest.builder()
                .productPublicId(productPublicId)
                .quantity(new BigDecimal("1.000"))
                .build();

        ProductDetailResponse disabledProduct = createProductDetail(productPublicId, "ACTIVE", false, true, new BigDecimal("40.00"), "KG");
        when(productServiceClient.getProductByPublicId(productPublicId)).thenReturn(disabledProduct);

        assertThrows(ProductUnavailableException.class, () -> cartService.addItem(customerId, request));
    }

    @Test
    @DisplayName("addItem rejects when inventory stock is insufficient")
    void testAddItem_RejectsInsufficientStock() {
        AddCartItemRequest request = AddCartItemRequest.builder()
                .productPublicId(productPublicId)
                .quantity(new BigDecimal("10.000"))
                .build();

        ProductDetailResponse product = createProductDetail(productPublicId, "ACTIVE", true, true, new BigDecimal("40.00"), "KG");
        when(productServiceClient.getProductByPublicId(productPublicId)).thenReturn(product);
        when(productServiceClient.checkProductAvailability(eq(productPublicId), any()))
                .thenReturn(new ProductAvailabilityResponse(productPublicId, "Fresh Spinach", true, true, "ACTIVE", null, new BigDecimal("10.000"), new BigDecimal("40.00"), new BigDecimal("400.00"), null));

        StockAvailabilityResponse stock = createStockResponse(productPublicId, new BigDecimal("2.000"), false, true, "KG");
        when(inventoryServiceClient.checkStockAvailability(eq(productPublicId), any())).thenReturn(stock);

        assertThrows(InsufficientStockException.class, () -> cartService.addItem(customerId, request));
    }

    @Test
    @DisplayName("updateItem successfully updates quantity")
    void testUpdateItem_Success() {
        UpdateCartItemRequest request = UpdateCartItemRequest.builder()
                .quantity(new BigDecimal("5.000"))
                .build();

        Cart cart = new Cart();
        cart.setId(1L);
        cart.setPublicId("cart-1");
        cart.setCustomerPublicId(customerId);
        cart.setStatus(CartStatus.ACTIVE);

        CartItem item = new CartItem();
        item.setId(10L);
        item.setPublicId("item-10");
        item.setProductPublicId(productPublicId);
        item.setProductNameSnapshot("Fresh Spinach");
        item.setQuantity(new BigDecimal("1.000"));
        item.setUnitOfMeasure("KG");
        item.setPricePerKgSnapshot(new BigDecimal("40.00"));
        cart.addItem(item);

        when(cartRepository.findByCustomerPublicIdAndStatus(customerId, CartStatus.ACTIVE))
                .thenReturn(Optional.of(cart));
        when(cartItemRepository.findByPublicIdAndCartId("item-10", 1L))
                .thenReturn(Optional.of(item));

        ProductDetailResponse product = createProductDetail(productPublicId, "ACTIVE", true, true, new BigDecimal("40.00"), "KG");
        when(productServiceClient.getProductByPublicId(productPublicId)).thenReturn(product);
        when(productServiceClient.checkProductAvailability(eq(productPublicId), any()))
                .thenReturn(new ProductAvailabilityResponse(productPublicId, "Fresh Spinach", true, true, "ACTIVE", null, new BigDecimal("5.000"), new BigDecimal("40.00"), new BigDecimal("200.00"), null));

        StockAvailabilityResponse stock = createStockResponse(productPublicId, new BigDecimal("50.000"), true, true, "KG");
        when(inventoryServiceClient.checkStockAvailability(eq(productPublicId), any())).thenReturn(stock);

        when(cartRepository.save(any(Cart.class))).thenReturn(cart);

        CartResponse response = cartService.updateItem(customerId, "item-10", request);

        assertNotNull(response);
        assertEquals(new BigDecimal("5.000"), item.getQuantity());
    }

    @Test
    @DisplayName("removeItem removes item from active cart")
    void testRemoveItem_Success() {
        Cart cart = new Cart();
        cart.setId(1L);
        cart.setPublicId("cart-1");
        cart.setCustomerPublicId(customerId);
        cart.setStatus(CartStatus.ACTIVE);

        CartItem item = new CartItem();
        item.setId(10L);
        item.setPublicId("item-10");
        item.setProductPublicId(productPublicId);
        item.setQuantity(new BigDecimal("1.000"));
        item.setPricePerKgSnapshot(new BigDecimal("40.00"));
        item.setUnitOfMeasure("KG");
        cart.addItem(item);

        when(cartRepository.findByCustomerPublicIdAndStatus(customerId, CartStatus.ACTIVE))
                .thenReturn(Optional.of(cart));
        when(cartItemRepository.findByPublicIdAndCartId("item-10", 1L))
                .thenReturn(Optional.of(item));
        when(cartRepository.save(any(Cart.class))).thenReturn(cart);

        CartResponse response = cartService.removeItem(customerId, "item-10");

        assertNotNull(response);
        assertEquals(0, response.getTotalItems());
    }

    @Test
    @DisplayName("clearCart removes all items")
    void testClearCart_Success() {
        Cart cart = new Cart();
        cart.setId(1L);
        cart.setPublicId("cart-1");
        cart.setCustomerPublicId(customerId);
        cart.setStatus(CartStatus.ACTIVE);

        CartItem item = new CartItem();
        item.setPublicId("item-10");
        item.setProductPublicId(productPublicId);
        item.setQuantity(new BigDecimal("1.000"));
        item.setPricePerKgSnapshot(new BigDecimal("40.00"));
        item.setUnitOfMeasure("KG");
        cart.addItem(item);

        when(cartRepository.findByCustomerPublicIdAndStatus(customerId, CartStatus.ACTIVE))
                .thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenReturn(cart);

        CartResponse response = cartService.clearCart(customerId);

        assertNotNull(response);
        assertEquals(0, response.getTotalItems());
        assertTrue(cart.getItems().isEmpty());
    }

    @Test
    @DisplayName("validateCart validates price change and stock sufficiency accurately")
    void testValidateCart_WithPriceChangeAndInStock() {
        Cart cart = new Cart();
        cart.setId(1L);
        cart.setPublicId("cart-1");
        cart.setCustomerPublicId(customerId);
        cart.setStatus(CartStatus.ACTIVE);
        cart.setCurrency("INR");

        CartItem item = new CartItem();
        item.setPublicId("item-1");
        item.setProductPublicId(productPublicId);
        item.setProductNameSnapshot("Spinach");
        item.setQuantity(new BigDecimal("2.000"));
        item.setUnitOfMeasure("KG");
        item.setPricePerKgSnapshot(new BigDecimal("30.00")); // Stored snapshot 30
        cart.addItem(item);

        when(cartRepository.findByCustomerPublicIdAndStatus(customerId, CartStatus.ACTIVE))
                .thenReturn(Optional.of(cart));

        // Live price changed to 35.00
        ProductDetailResponse product = createProductDetail(productPublicId, "ACTIVE", true, true, new BigDecimal("35.00"), "KG");
        when(productServiceClient.getProductByPublicId(productPublicId)).thenReturn(product);

        StockAvailabilityResponse stock = createStockResponse(productPublicId, new BigDecimal("10.000"), true, true, "KG");
        when(inventoryServiceClient.checkStockAvailability(productPublicId, new BigDecimal("2.000"))).thenReturn(stock);

        CartValidationResponse response = cartService.validateCart(customerId);

        assertNotNull(response);
        assertTrue(response.isValid(), "Cart is valid since product is active and in stock");
        assertTrue(response.isPriceChanged(), "Price change should be flagged");
        assertEquals(new BigDecimal("60.00"), response.getPreviousSubtotal()); // 2 * 30
        assertEquals(new BigDecimal("70.00"), response.getCurrentSubtotal());  // 2 * 35
        assertEquals(CartItemValidationStatus.PRICE_CHANGED, response.getItems().get(0).getStatus());
    }

    @Test
    @DisplayName("validateCart reports invalid when product is out of stock")
    void testValidateCart_OutOfStock() {
        Cart cart = new Cart();
        cart.setId(1L);
        cart.setPublicId("cart-1");
        cart.setCustomerPublicId(customerId);
        cart.setStatus(CartStatus.ACTIVE);

        CartItem item = new CartItem();
        item.setPublicId("item-1");
        item.setProductPublicId(productPublicId);
        item.setProductNameSnapshot("Spinach");
        item.setQuantity(new BigDecimal("2.000"));
        item.setUnitOfMeasure("KG");
        item.setPricePerKgSnapshot(new BigDecimal("30.00"));
        cart.addItem(item);

        when(cartRepository.findByCustomerPublicIdAndStatus(customerId, CartStatus.ACTIVE))
                .thenReturn(Optional.of(cart));

        ProductDetailResponse product = createProductDetail(productPublicId, "ACTIVE", true, true, new BigDecimal("30.00"), "KG");
        when(productServiceClient.getProductByPublicId(productPublicId)).thenReturn(product);

        StockAvailabilityResponse stock = createStockResponse(productPublicId, BigDecimal.ZERO, false, false, "KG");
        when(inventoryServiceClient.checkStockAvailability(productPublicId, new BigDecimal("2.000"))).thenReturn(stock);

        CartValidationResponse response = cartService.validateCart(customerId);

        assertNotNull(response);
        assertFalse(response.isValid(), "Cart must be invalid when out of stock");
        assertEquals(1, response.getInsufficientStockCount());
        assertEquals(CartItemValidationStatus.OUT_OF_STOCK, response.getItems().get(0).getStatus());
    }

    @Test
    @DisplayName("expireAbandonedCarts marks batch of expired carts as EXPIRED")
    void testExpireAbandonedCarts() {
        Cart expiredCart1 = new Cart();
        expiredCart1.setId(10L);
        Cart expiredCart2 = new Cart();
        expiredCart2.setId(20L);

        when(cartRepository.findByStatusAndExpiresAtBefore(eq(CartStatus.ACTIVE), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(expiredCart1, expiredCart2)));
        when(cartRepository.updateStatusForCartIds(List.of(10L, 20L), CartStatus.EXPIRED)).thenReturn(2);

        int count = cartService.expireAbandonedCarts();

        assertEquals(2, count);
        verify(cartRepository, times(1)).updateStatusForCartIds(List.of(10L, 20L), CartStatus.EXPIRED);
    }
}
