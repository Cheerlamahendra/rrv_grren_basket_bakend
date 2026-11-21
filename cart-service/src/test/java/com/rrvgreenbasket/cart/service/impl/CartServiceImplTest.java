package com.rrvgreenbasket.cart.service.impl;

import com.rrvgreenbasket.cart.client.ProductServiceClient;
import com.rrvgreenbasket.cart.dto.client.ProductAvailabilityResponse;
import com.rrvgreenbasket.cart.dto.client.ProductDetailResponse;
import com.rrvgreenbasket.cart.dto.request.AddCartItemRequest;
import com.rrvgreenbasket.cart.dto.request.UpdateCartItemRequest;
import com.rrvgreenbasket.cart.dto.response.CartResponse;
import com.rrvgreenbasket.cart.entity.Cart;
import com.rrvgreenbasket.cart.entity.CartItem;
import com.rrvgreenbasket.cart.entity.CartStatus;
import com.rrvgreenbasket.cart.exception.CartItemNotFoundException;
import com.rrvgreenbasket.cart.exception.CartNotFoundException;
import com.rrvgreenbasket.cart.exception.ProductUnavailableException;
import com.rrvgreenbasket.cart.mapper.CartResponseMapper;
import com.rrvgreenbasket.cart.repository.CartItemRepository;
import com.rrvgreenbasket.cart.repository.CartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductServiceClient productServiceClient;

    private CartResponseMapper cartResponseMapper;
    private CartServiceImpl cartService;

    private static final String CUSTOMER_ID = "cust-123";
    private static final String PRODUCT_ID = "prod-456";
    private static final String CART_ITEM_ID = "item-789";

    private Cart activeCart;

    @BeforeEach
    void setUp() {
        cartResponseMapper = new CartResponseMapper();
        cartService = new CartServiceImpl(
                cartRepository,
                cartItemRepository,
                cartResponseMapper,
                productServiceClient
        );

        activeCart = new Cart();
        activeCart.setId(1L);
        activeCart.setPublicId("cart-pub-1");
        activeCart.setCustomerPublicId(CUSTOMER_ID);
        activeCart.setStatus(CartStatus.ACTIVE);
        activeCart.setItems(new ArrayList<>());
    }

    private ProductDetailResponse createProductDetail(boolean available, String status, BigDecimal price, String unit) {
        return new ProductDetailResponse(
                PRODUCT_ID,
                "Fresh Spinach",
                price,
                new BigDecimal("1.000"),
                unit,
                price,
                "http://example.com/spinach.jpg",
                status,
                true,
                false,
                available,
                "IN_STOCK"
        );
    }

    private ProductAvailabilityResponse createProductAvailability(boolean available, String reason) {
        return new ProductAvailabilityResponse(
                PRODUCT_ID,
                "Fresh Spinach",
                available,
                true,
                "ACTIVE",
                "IN_STOCK",
                new BigDecimal("2.000"),
                new BigDecimal("40.00"),
                new BigDecimal("80.00"),
                reason
        );
    }

    @Test
    @DisplayName("getMyCart returns existing active cart")
    void getMyCart_returnsExistingActiveCart() {
        when(cartRepository.findByCustomerPublicIdAndStatus(CUSTOMER_ID, CartStatus.ACTIVE))
                .thenReturn(Optional.of(activeCart));

        CartResponse result = cartService.getMyCart(CUSTOMER_ID);

        assertThat(result).isNotNull();
        assertThat(result.publicId()).isEqualTo("cart-pub-1");
        assertThat(result.status()).isEqualTo(CartStatus.ACTIVE);
        verify(cartRepository).findByCustomerPublicIdAndStatus(CUSTOMER_ID, CartStatus.ACTIVE);
        verify(cartRepository, never()).save(any());
    }

    @Test
    @DisplayName("getMyCart creates a new active cart when none exists")
    void getMyCart_createsNewCartWhenNoneExists() {
        when(cartRepository.findByCustomerPublicIdAndStatus(CUSTOMER_ID, CartStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> {
            Cart cart = invocation.getArgument(0);
            cart.setPublicId("cart-new-1");
            return cart;
        });

        CartResponse result = cartService.getMyCart(CUSTOMER_ID);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(CartStatus.ACTIVE);
        verify(cartRepository).save(argThat(c ->
                CUSTOMER_ID.equals(c.getCustomerPublicId()) && c.getStatus() == CartStatus.ACTIVE
        ));
    }

    @Test
    @DisplayName("addItem successfully adds new product using Product Service only")
    void addItem_success_newProduct_callsProductServiceOnly() {
        AddCartItemRequest request = new AddCartItemRequest(PRODUCT_ID, new BigDecimal("2.000"));
        ProductDetailResponse productDetail = createProductDetail(true, "ACTIVE", new BigDecimal("45.00"), "KG");
        ProductAvailabilityResponse availability = createProductAvailability(true, null);

        when(productServiceClient.getProductByPublicId(PRODUCT_ID)).thenReturn(productDetail);
        when(productServiceClient.checkProductAvailability(PRODUCT_ID, new BigDecimal("2.000"))).thenReturn(availability);
        when(cartRepository.findByCustomerPublicIdAndStatus(CUSTOMER_ID, CartStatus.ACTIVE)).thenReturn(Optional.of(activeCart));
        when(cartItemRepository.findByCartIdAndProductPublicIdAndUnitOfMeasure(activeCart.getId(), PRODUCT_ID, "KG"))
                .thenReturn(Optional.empty());
        when(cartRepository.save(activeCart)).thenReturn(activeCart);

        CartResponse response = cartService.addItem(CUSTOMER_ID, request);

        assertThat(response).isNotNull();
        assertThat(activeCart.getItems()).hasSize(1);
        CartItem addedItem = activeCart.getItems().get(0);
        assertThat(addedItem.getProductPublicId()).isEqualTo(PRODUCT_ID);
        assertThat(addedItem.getProductNameSnapshot()).isEqualTo("Fresh Spinach");
        assertThat(addedItem.getQuantity()).isEqualByComparingTo("2.000");
        assertThat(addedItem.getUnitOfMeasure()).isEqualTo("KG");
        assertThat(addedItem.getPricePerKgSnapshot()).isEqualByComparingTo("45.00");
        assertThat(addedItem.getProductImageUrlSnapshot()).isEqualTo("http://example.com/spinach.jpg");

        verify(productServiceClient).getProductByPublicId(PRODUCT_ID);
        verify(productServiceClient).checkProductAvailability(PRODUCT_ID, new BigDecimal("2.000"));
        verify(cartRepository).save(activeCart);
    }

    @Test
    @DisplayName("addItem increases quantity of existing product and rechecks combined availability")
    void addItem_success_existingProduct_increasesQuantity() {
        AddCartItemRequest request = new AddCartItemRequest(PRODUCT_ID, new BigDecimal("2.000"));
        ProductDetailResponse productDetail = createProductDetail(true, "ACTIVE", new BigDecimal("45.00"), "KG");
        ProductAvailabilityResponse initialAvailability = createProductAvailability(true, null);
        ProductAvailabilityResponse combinedAvailability = createProductAvailability(true, null);

        CartItem existingItem = new CartItem();
        existingItem.setId(10L);
        existingItem.setProductPublicId(PRODUCT_ID);
        existingItem.setQuantity(new BigDecimal("3.000"));
        existingItem.setUnitOfMeasure("KG");
        existingItem.setPricePerKgSnapshot(new BigDecimal("40.00"));
        existingItem.setProductNameSnapshot("Fresh Spinach Old");
        activeCart.addItem(existingItem);

        when(productServiceClient.getProductByPublicId(PRODUCT_ID)).thenReturn(productDetail);
        when(productServiceClient.checkProductAvailability(PRODUCT_ID, new BigDecimal("2.000"))).thenReturn(initialAvailability);
        when(cartRepository.findByCustomerPublicIdAndStatus(CUSTOMER_ID, CartStatus.ACTIVE)).thenReturn(Optional.of(activeCart));
        when(cartItemRepository.findByCartIdAndProductPublicIdAndUnitOfMeasure(activeCart.getId(), PRODUCT_ID, "KG"))
                .thenReturn(Optional.of(existingItem));
        when(productServiceClient.checkProductAvailability(PRODUCT_ID, new BigDecimal("5.000"))).thenReturn(combinedAvailability);
        when(cartRepository.save(activeCart)).thenReturn(activeCart);

        CartResponse response = cartService.addItem(CUSTOMER_ID, request);

        assertThat(response).isNotNull();
        assertThat(existingItem.getQuantity()).isEqualByComparingTo("5.000");
        assertThat(existingItem.getProductNameSnapshot()).isEqualTo("Fresh Spinach");
        assertThat(existingItem.getPricePerKgSnapshot()).isEqualByComparingTo("45.00");

        verify(productServiceClient).checkProductAvailability(PRODUCT_ID, new BigDecimal("2.000"));
        verify(productServiceClient).checkProductAvailability(PRODUCT_ID, new BigDecimal("5.000"));
        verify(cartRepository).save(activeCart);
    }

    @Test
    @DisplayName("addItem throws ProductUnavailableException when product is not available")
    void addItem_throwsProductUnavailable_whenProductNotAvailable() {
        AddCartItemRequest request = new AddCartItemRequest(PRODUCT_ID, new BigDecimal("1.000"));
        ProductDetailResponse productDetail = createProductDetail(false, "ACTIVE", new BigDecimal("45.00"), "KG");
        when(productServiceClient.getProductByPublicId(PRODUCT_ID)).thenReturn(productDetail);

        assertThatThrownBy(() -> cartService.addItem(CUSTOMER_ID, request))
                .isInstanceOf(ProductUnavailableException.class)
                .hasMessageContaining("currently unavailable");

        verify(productServiceClient, never()).checkProductAvailability(any(), any());
        verify(cartRepository, never()).save(any());
    }

    @Test
    @DisplayName("addItem throws ProductUnavailableException when product status is not ACTIVE")
    void addItem_throwsProductUnavailable_whenProductInactive() {
        AddCartItemRequest request = new AddCartItemRequest(PRODUCT_ID, new BigDecimal("1.000"));
        ProductDetailResponse productDetail = createProductDetail(true, "INACTIVE", new BigDecimal("45.00"), "KG");
        when(productServiceClient.getProductByPublicId(PRODUCT_ID)).thenReturn(productDetail);

        assertThatThrownBy(() -> cartService.addItem(CUSTOMER_ID, request))
                .isInstanceOf(ProductUnavailableException.class)
                .hasMessageContaining("currently unavailable");
    }

    @Test
    @DisplayName("addItem throws ProductUnavailableException when requested quantity is unavailable")
    void addItem_throwsProductUnavailable_whenQuantityUnavailable() {
        AddCartItemRequest request = new AddCartItemRequest(PRODUCT_ID, new BigDecimal("10.000"));
        ProductDetailResponse productDetail = createProductDetail(true, "ACTIVE", new BigDecimal("45.00"), "KG");
        ProductAvailabilityResponse availability = createProductAvailability(false, "Requested quantity exceeds available limit");

        when(productServiceClient.getProductByPublicId(PRODUCT_ID)).thenReturn(productDetail);
        when(productServiceClient.checkProductAvailability(PRODUCT_ID, new BigDecimal("10.000"))).thenReturn(availability);

        assertThatThrownBy(() -> cartService.addItem(CUSTOMER_ID, request))
                .isInstanceOf(ProductUnavailableException.class)
                .hasMessage("Requested quantity exceeds available limit");
    }

    @Test
    @DisplayName("addItem throws IllegalStateException when price is null or invalid")
    void addItem_throwsIllegalState_whenPriceInvalid() {
        AddCartItemRequest request = new AddCartItemRequest(PRODUCT_ID, new BigDecimal("1.000"));
        ProductDetailResponse productDetail = createProductDetail(true, "ACTIVE", BigDecimal.ZERO, "KG");
        ProductAvailabilityResponse availability = createProductAvailability(true, null);

        when(productServiceClient.getProductByPublicId(PRODUCT_ID)).thenReturn(productDetail);
        when(productServiceClient.checkProductAvailability(PRODUCT_ID, new BigDecimal("1.000"))).thenReturn(availability);

        assertThatThrownBy(() -> cartService.addItem(CUSTOMER_ID, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid product price");
    }

    @Test
    @DisplayName("addItem throws IllegalStateException when quantityUnit is missing")
    void addItem_throwsIllegalState_whenUnitMissing() {
        AddCartItemRequest request = new AddCartItemRequest(PRODUCT_ID, new BigDecimal("1.000"));
        ProductDetailResponse productDetail = createProductDetail(true, "ACTIVE", new BigDecimal("45.00"), "");
        ProductAvailabilityResponse availability = createProductAvailability(true, null);

        when(productServiceClient.getProductByPublicId(PRODUCT_ID)).thenReturn(productDetail);
        when(productServiceClient.checkProductAvailability(PRODUCT_ID, new BigDecimal("1.000"))).thenReturn(availability);

        assertThatThrownBy(() -> cartService.addItem(CUSTOMER_ID, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unit of measure is missing");
    }

    @Test
    @DisplayName("addItem throws ProductUnavailableException when combined quantity exceeds availability")
    void addItem_throwsProductUnavailable_whenCombinedQuantityUnavailable() {
        AddCartItemRequest request = new AddCartItemRequest(PRODUCT_ID, new BigDecimal("2.000"));
        ProductDetailResponse productDetail = createProductDetail(true, "ACTIVE", new BigDecimal("45.00"), "KG");
        ProductAvailabilityResponse initialAvailability = createProductAvailability(true, null);
        ProductAvailabilityResponse combinedAvailability = createProductAvailability(false, "Combined quantity exceeds stock limit");

        CartItem existingItem = new CartItem();
        existingItem.setId(10L);
        existingItem.setProductPublicId(PRODUCT_ID);
        existingItem.setQuantity(new BigDecimal("3.000"));
        existingItem.setUnitOfMeasure("KG");
        existingItem.setPricePerKgSnapshot(new BigDecimal("45.00"));
        activeCart.addItem(existingItem);

        when(productServiceClient.getProductByPublicId(PRODUCT_ID)).thenReturn(productDetail);
        when(productServiceClient.checkProductAvailability(PRODUCT_ID, new BigDecimal("2.000"))).thenReturn(initialAvailability);
        when(cartRepository.findByCustomerPublicIdAndStatus(CUSTOMER_ID, CartStatus.ACTIVE)).thenReturn(Optional.of(activeCart));
        when(cartItemRepository.findByCartIdAndProductPublicIdAndUnitOfMeasure(activeCart.getId(), PRODUCT_ID, "KG"))
                .thenReturn(Optional.of(existingItem));
        when(productServiceClient.checkProductAvailability(PRODUCT_ID, new BigDecimal("5.000"))).thenReturn(combinedAvailability);

        assertThatThrownBy(() -> cartService.addItem(CUSTOMER_ID, request))
                .isInstanceOf(ProductUnavailableException.class)
                .hasMessage("Combined quantity exceeds stock limit");

        verify(cartRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateItem successfully updates item quantity using Product Service only")
    void updateItem_success() {
        UpdateCartItemRequest request = new UpdateCartItemRequest(new BigDecimal("4.500"));
        CartItem item = new CartItem();
        item.setId(20L);
        item.setPublicId(CART_ITEM_ID);
        item.setProductPublicId(PRODUCT_ID);
        item.setQuantity(new BigDecimal("2.000"));
        item.setUnitOfMeasure("KG");
        item.setPricePerKgSnapshot(new BigDecimal("40.00"));
        item.setProductNameSnapshot("Old Name");
        activeCart.addItem(item);

        ProductDetailResponse productDetail = createProductDetail(true, "ACTIVE", new BigDecimal("50.00"), "KG");
        ProductAvailabilityResponse availability = createProductAvailability(true, null);

        when(cartRepository.findByCustomerPublicIdAndStatus(CUSTOMER_ID, CartStatus.ACTIVE)).thenReturn(Optional.of(activeCart));
        when(cartItemRepository.findByPublicIdAndCartId(CART_ITEM_ID, activeCart.getId())).thenReturn(Optional.of(item));
        when(productServiceClient.getProductByPublicId(PRODUCT_ID)).thenReturn(productDetail);
        when(productServiceClient.checkProductAvailability(PRODUCT_ID, new BigDecimal("4.500"))).thenReturn(availability);
        when(cartRepository.save(activeCart)).thenReturn(activeCart);

        CartResponse response = cartService.updateItem(CUSTOMER_ID, CART_ITEM_ID, request);

        assertThat(response).isNotNull();
        assertThat(item.getQuantity()).isEqualByComparingTo("4.500");
        assertThat(item.getProductNameSnapshot()).isEqualTo("Fresh Spinach");
        assertThat(item.getPricePerKgSnapshot()).isEqualByComparingTo("50.00");

        verify(productServiceClient).getProductByPublicId(PRODUCT_ID);
        verify(productServiceClient).checkProductAvailability(PRODUCT_ID, new BigDecimal("4.500"));
        verify(cartRepository).save(activeCart);
    }

    @Test
    @DisplayName("updateItem throws CartNotFoundException when no active cart exists")
    void updateItem_throwsCartNotFound_whenNoActiveCart() {
        UpdateCartItemRequest request = new UpdateCartItemRequest(new BigDecimal("3.000"));
        when(cartRepository.findByCustomerPublicIdAndStatus(CUSTOMER_ID, CartStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.updateItem(CUSTOMER_ID, CART_ITEM_ID, request))
                .isInstanceOf(CartNotFoundException.class)
                .hasMessageContaining("Active cart not found");
    }

    @Test
    @DisplayName("updateItem throws CartItemNotFoundException when item does not belong to customer cart")
    void updateItem_throwsCartItemNotFound_whenItemNotInCart() {
        UpdateCartItemRequest request = new UpdateCartItemRequest(new BigDecimal("3.000"));
        when(cartRepository.findByCustomerPublicIdAndStatus(CUSTOMER_ID, CartStatus.ACTIVE)).thenReturn(Optional.of(activeCart));
        when(cartItemRepository.findByPublicIdAndCartId(CART_ITEM_ID, activeCart.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.updateItem(CUSTOMER_ID, CART_ITEM_ID, request))
                .isInstanceOf(CartItemNotFoundException.class)
                .hasMessageContaining("Cart item not found");
    }

    @Test
    @DisplayName("updateItem throws ProductUnavailableException when product is unavailable or inactive")
    void updateItem_throwsProductUnavailable_whenProductUnavailable() {
        UpdateCartItemRequest request = new UpdateCartItemRequest(new BigDecimal("3.000"));
        CartItem item = new CartItem();
        item.setId(20L);
        item.setPublicId(CART_ITEM_ID);
        item.setProductPublicId(PRODUCT_ID);
        item.setUnitOfMeasure("KG");
        activeCart.addItem(item);

        ProductDetailResponse productDetail = createProductDetail(false, "ACTIVE", new BigDecimal("50.00"), "KG");

        when(cartRepository.findByCustomerPublicIdAndStatus(CUSTOMER_ID, CartStatus.ACTIVE)).thenReturn(Optional.of(activeCart));
        when(cartItemRepository.findByPublicIdAndCartId(CART_ITEM_ID, activeCart.getId())).thenReturn(Optional.of(item));
        when(productServiceClient.getProductByPublicId(PRODUCT_ID)).thenReturn(productDetail);

        assertThatThrownBy(() -> cartService.updateItem(CUSTOMER_ID, CART_ITEM_ID, request))
                .isInstanceOf(ProductUnavailableException.class)
                .hasMessageContaining("currently unavailable");
    }

    @Test
    @DisplayName("updateItem throws IllegalStateException when quantity unit mismatches existing cart item")
    void updateItem_throwsIllegalState_whenUnitMismatches() {
        UpdateCartItemRequest request = new UpdateCartItemRequest(new BigDecimal("3.000"));
        CartItem item = new CartItem();
        item.setId(20L);
        item.setPublicId(CART_ITEM_ID);
        item.setProductPublicId(PRODUCT_ID);
        item.setUnitOfMeasure("KG");
        activeCart.addItem(item);

        ProductDetailResponse productDetail = createProductDetail(true, "ACTIVE", new BigDecimal("50.00"), "GRAM");
        ProductAvailabilityResponse availability = createProductAvailability(true, null);

        when(cartRepository.findByCustomerPublicIdAndStatus(CUSTOMER_ID, CartStatus.ACTIVE)).thenReturn(Optional.of(activeCart));
        when(cartItemRepository.findByPublicIdAndCartId(CART_ITEM_ID, activeCart.getId())).thenReturn(Optional.of(item));
        when(productServiceClient.getProductByPublicId(PRODUCT_ID)).thenReturn(productDetail);
        when(productServiceClient.checkProductAvailability(PRODUCT_ID, new BigDecimal("3.000"))).thenReturn(availability);

        assertThatThrownBy(() -> cartService.updateItem(CUSTOMER_ID, CART_ITEM_ID, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match cart item");
    }

    @Test
    @DisplayName("removeItem removes item from active cart")
    void removeItem_success() {
        CartItem item = new CartItem();
        item.setId(20L);
        item.setPublicId(CART_ITEM_ID);
        activeCart.addItem(item);

        when(cartRepository.findByCustomerPublicIdAndStatus(CUSTOMER_ID, CartStatus.ACTIVE)).thenReturn(Optional.of(activeCart));
        when(cartItemRepository.findByPublicIdAndCartId(CART_ITEM_ID, activeCart.getId())).thenReturn(Optional.of(item));
        when(cartRepository.save(activeCart)).thenReturn(activeCart);

        CartResponse response = cartService.removeItem(CUSTOMER_ID, CART_ITEM_ID);

        assertThat(response).isNotNull();
        assertThat(activeCart.getItems()).doesNotContain(item);
        verify(cartRepository).save(activeCart);
    }

    @Test
    @DisplayName("removeItem throws CartItemNotFoundException when item not in customer cart")
    void removeItem_throwsCartItemNotFound() {
        when(cartRepository.findByCustomerPublicIdAndStatus(CUSTOMER_ID, CartStatus.ACTIVE)).thenReturn(Optional.of(activeCart));
        when(cartItemRepository.findByPublicIdAndCartId(CART_ITEM_ID, activeCart.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.removeItem(CUSTOMER_ID, CART_ITEM_ID))
                .isInstanceOf(CartItemNotFoundException.class)
                .hasMessageContaining("Cart item not found");
    }

    @Test
    @DisplayName("clearCart removes all items from active cart")
    void clearCart_success() {
        CartItem item1 = new CartItem();
        CartItem item2 = new CartItem();
        activeCart.addItem(item1);
        activeCart.addItem(item2);

        when(cartRepository.findByCustomerPublicIdAndStatus(CUSTOMER_ID, CartStatus.ACTIVE)).thenReturn(Optional.of(activeCart));
        when(cartRepository.save(activeCart)).thenReturn(activeCart);

        CartResponse response = cartService.clearCart(CUSTOMER_ID);

        assertThat(response).isNotNull();
        assertThat(activeCart.getItems()).isEmpty();
        verify(cartRepository).save(activeCart);
    }
}
