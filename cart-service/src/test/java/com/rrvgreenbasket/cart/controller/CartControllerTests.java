package com.rrvgreenbasket.cart.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.cart.dto.request.AddCartItemRequest;
import com.rrvgreenbasket.cart.dto.request.UpdateCartItemRequest;
import com.rrvgreenbasket.cart.dto.response.*;
import com.rrvgreenbasket.cart.entity.CartStatus;
import com.rrvgreenbasket.cart.security.HeaderAuthenticationFilter;
import com.rrvgreenbasket.cart.security.UserPrincipal;
import com.rrvgreenbasket.cart.service.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CartController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("CartController MockMvc Tests")
class CartControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CartService cartService;

    @MockBean
    private HeaderAuthenticationFilter headerAuthenticationFilter;

    private UserPrincipal customerPrincipal;

    @BeforeEach
    void setUp() {
        customerPrincipal = new UserPrincipal("cust-uuid-1", "CUSTOMER", "sess-1");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(customerPrincipal, null, customerPrincipal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private CartResponse buildSampleCartResponse() {
        return CartResponse.builder()
                .cartPublicId("cart-uuid-1")
                .publicId("cart-uuid-1")
                .status(CartStatus.ACTIVE)
                .totalItems(1)
                .itemCount(1)
                .subtotal(new BigDecimal("90.00"))
                .currency("INR")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .items(List.of(
                        CartItemResponse.builder()
                                .itemPublicId("item-uuid-1")
                                .publicId("item-uuid-1")
                                .productPublicId("prod-uuid-1")
                                .productName("Tomato")
                                .quantity(new BigDecimal("2.000"))
                                .unitOfMeasure("KG")
                                .previousUnitPrice(new BigDecimal("45.00"))
                                .currentUnitPrice(new BigDecimal("45.00"))
                                .itemSubtotal(new BigDecimal("90.00"))
                                .build()
                ))
                .build();
    }

    @Test
    @DisplayName("GET /api/cart returns customer's cart")
    void testGetMyCart() throws Exception {
        when(cartService.getMyCart("cust-uuid-1")).thenReturn(buildSampleCartResponse());

        mockMvc.perform(get("/api/cart").principal(customerPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.cartPublicId").value("cart-uuid-1"))
                .andExpect(jsonPath("$.data.subtotal").value(90.00));
    }

    @Test
    @DisplayName("POST /api/cart/items adds item with 201 Created")
    void testAddItem() throws Exception {
        AddCartItemRequest request = AddCartItemRequest.builder()
                .productPublicId("prod-uuid-1")
                .quantity(new BigDecimal("2.000"))
                .unitOfMeasure("KG")
                .build();

        when(cartService.addItem(eq("cust-uuid-1"), any())).thenReturn(buildSampleCartResponse());

        mockMvc.perform(post("/api/cart/items")
                        .principal(customerPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Item added to cart successfully"));
    }

    @Test
    @DisplayName("PATCH /api/cart/items/{id} updates item quantity")
    void testUpdateItemPatch() throws Exception {
        UpdateCartItemRequest request = UpdateCartItemRequest.builder()
                .quantity(new BigDecimal("3.000"))
                .build();

        when(cartService.updateItem(eq("cust-uuid-1"), eq("item-uuid-1"), any())).thenReturn(buildSampleCartResponse());

        mockMvc.perform(patch("/api/cart/items/item-uuid-1")
                        .principal(customerPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("PUT /api/cart/items/{id} updates item quantity (backward compatible)")
    void testUpdateItemPut() throws Exception {
        UpdateCartItemRequest request = UpdateCartItemRequest.builder()
                .quantity(new BigDecimal("3.000"))
                .build();

        when(cartService.updateItem(eq("cust-uuid-1"), eq("item-uuid-1"), any())).thenReturn(buildSampleCartResponse());

        mockMvc.perform(put("/api/cart/items/item-uuid-1")
                        .principal(customerPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /api/cart/items/{id} removes item")
    void testRemoveItem() throws Exception {
        when(cartService.removeItem("cust-uuid-1", "item-uuid-1")).thenReturn(buildSampleCartResponse());

        mockMvc.perform(delete("/api/cart/items/item-uuid-1").principal(customerPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /api/cart clears active cart")
    void testClearCart() throws Exception {
        when(cartService.clearCart("cust-uuid-1")).thenReturn(buildSampleCartResponse());

        mockMvc.perform(delete("/api/cart").principal(customerPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/cart/validate returns validation response")
    void testValidateCart() throws Exception {
        CartValidationResponse validationResponse = CartValidationResponse.builder()
                .cartPublicId("cart-uuid-1")
                .valid(true)
                .totalItems(1)
                .currentSubtotal(new BigDecimal("90.00"))
                .previousSubtotal(new BigDecimal("90.00"))
                .currency("INR")
                .priceChanged(false)
                .validationMessages(List.of("All cart items are verified and available for checkout."))
                .build();

        when(cartService.validateCart("cust-uuid-1")).thenReturn(validationResponse);

        mockMvc.perform(post("/api/cart/validate").principal(customerPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.cartPublicId").value("cart-uuid-1"));
    }
}
