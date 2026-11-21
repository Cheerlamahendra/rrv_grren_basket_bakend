package com.rrvgreenbasket.cart.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.cart.dto.request.AddCartItemRequest;
import com.rrvgreenbasket.cart.dto.request.UpdateCartItemRequest;
import com.rrvgreenbasket.cart.dto.response.CartItemResponse;
import com.rrvgreenbasket.cart.dto.response.CartResponse;
import com.rrvgreenbasket.cart.entity.CartStatus;
import com.rrvgreenbasket.cart.security.UserPrincipal;
import com.rrvgreenbasket.cart.service.CartService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class CartControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CartService cartService;

    @InjectMocks
    private CartController cartController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String CUSTOMER_ID = "cust-123";
    private static final String ITEM_ID = "item-456";
    private UserPrincipal testUser;

    @BeforeEach
    void setUp() {
        testUser = new UserPrincipal(CUSTOMER_ID, "CUSTOMER", "sess-1");

        HandlerMethodArgumentResolver authenticationPrincipalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                        && parameter.getParameterType().equals(UserPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return testUser;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(cartController)
                .setCustomArgumentResolvers(authenticationPrincipalResolver)
                .build();
    }

    private CartResponse sampleCartResponse() {
        CartItemResponse item = new CartItemResponse(
                ITEM_ID,
                "prod-1",
                "Tomato",
                "http://example.com/tomato.jpg",
                new BigDecimal("2.000"),
                "KG",
                new BigDecimal("30.00"),
                new BigDecimal("60.00")
        );
        return new CartResponse(
                "cart-1",
                CartStatus.ACTIVE,
                List.of(item),
                1,
                new BigDecimal("60.00"),
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("GET /api/cart returns active cart")
    void getMyCart_returnsCart() throws Exception {
        when(cartService.getMyCart(CUSTOMER_ID)).thenReturn(sampleCartResponse());

        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value("cart-1"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.itemCount").value(1))
                .andExpect(jsonPath("$.totalAmount").value(60.00));

        verify(cartService).getMyCart(CUSTOMER_ID);
    }

    @Test
    @DisplayName("POST /api/cart/items adds item and returns 201 Created")
    void addItem_returnsCreated() throws Exception {
        AddCartItemRequest request = new AddCartItemRequest("prod-1", new BigDecimal("2.000"));
        when(cartService.addItem(eq(CUSTOMER_ID), any(AddCartItemRequest.class))).thenReturn(sampleCartResponse());

        mockMvc.perform(post("/api/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.publicId").value("cart-1"));

        verify(cartService).addItem(eq(CUSTOMER_ID), any(AddCartItemRequest.class));
    }

    @Test
    @DisplayName("PUT /api/cart/items/{cartItemPublicId} updates item quantity")
    void updateItem_returnsOk() throws Exception {
        UpdateCartItemRequest request = new UpdateCartItemRequest(new BigDecimal("3.000"));
        when(cartService.updateItem(eq(CUSTOMER_ID), eq(ITEM_ID), any(UpdateCartItemRequest.class)))
                .thenReturn(sampleCartResponse());

        mockMvc.perform(put("/api/cart/items/{cartItemPublicId}", ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value("cart-1"));

        verify(cartService).updateItem(eq(CUSTOMER_ID), eq(ITEM_ID), any(UpdateCartItemRequest.class));
    }

    @Test
    @DisplayName("DELETE /api/cart/items/{cartItemPublicId} removes single item")
    void removeItem_returnsOk() throws Exception {
        when(cartService.removeItem(CUSTOMER_ID, ITEM_ID)).thenReturn(sampleCartResponse());

        mockMvc.perform(delete("/api/cart/items/{cartItemPublicId}", ITEM_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value("cart-1"));

        verify(cartService).removeItem(CUSTOMER_ID, ITEM_ID);
    }

    @Test
    @DisplayName("DELETE /api/cart/items clears the cart")
    void clearCart_returnsOk() throws Exception {
        when(cartService.clearCart(CUSTOMER_ID)).thenReturn(sampleCartResponse());

        mockMvc.perform(delete("/api/cart/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value("cart-1"));

        verify(cartService).clearCart(CUSTOMER_ID);
    }

    @Test
    @DisplayName("DELETE /api/cart clears the cart")
    void clearCart_basePath_returnsOk() throws Exception {
        when(cartService.clearCart(CUSTOMER_ID)).thenReturn(sampleCartResponse());

        mockMvc.perform(delete("/api/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value("cart-1"));

        verify(cartService).clearCart(CUSTOMER_ID);
    }
}
