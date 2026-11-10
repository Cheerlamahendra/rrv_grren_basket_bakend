package com.rrvgreenbasket.wishlist;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.wishlist.controller.InternalWishlistController;
import com.rrvgreenbasket.wishlist.controller.WishlistController;
import com.rrvgreenbasket.wishlist.dto.request.AddToWishlistRequest;
import com.rrvgreenbasket.wishlist.dto.request.WishlistBulkStatusRequest;
import com.rrvgreenbasket.wishlist.dto.response.*;
import com.rrvgreenbasket.wishlist.exception.GlobalExceptionHandler;
import com.rrvgreenbasket.wishlist.security.UserPrincipal;
import com.rrvgreenbasket.wishlist.service.WishlistService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Web MVC Tests — Wishlist Controllers")
class WishlistControllerTests {

    private MockMvc wishlistMockMvc;
    private MockMvc internalMockMvc;

    @Mock
    private WishlistService wishlistService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String CUSTOMER_ID = "550e8400-e29b-41d4-a716-446655440001";
    private static final String PRODUCT_ID = "660e8400-e29b-41d4-a716-446655440001";

    @BeforeEach
    void setUp() {
        WishlistController wishlistController = new WishlistController(wishlistService);
        wishlistMockMvc = MockMvcBuilders.standaloneSetup(wishlistController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        InternalWishlistController internalController = new InternalWishlistController(wishlistService);
        internalMockMvc = MockMvcBuilders.standaloneSetup(internalController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        // Setup authenticated customer in SecurityContext
        UserPrincipal principal = UserPrincipal.create(CUSTOMER_ID, "customer@example.com", "ROLE_CUSTOMER", "sess-1", "ACTIVE");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /api/wishlist/items - Successfully adds product to customer's wishlist")
    void testAddProductToWishlistMvc() throws Exception {
        AddToWishlistRequest request = AddToWishlistRequest.builder()
                .productPublicId(PRODUCT_ID)
                .build();

        WishlistItemResponse response = WishlistItemResponse.builder()
                .productPublicId(PRODUCT_ID)
                .isWishlisted(true)
                .build();

        when(wishlistService.addProductToWishlist(CUSTOMER_ID, PRODUCT_ID)).thenReturn(response);

        wishlistMockMvc.perform(post("/api/wishlist/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Product added to wishlist successfully"))
                .andExpect(jsonPath("$.data.productPublicId").value(PRODUCT_ID))
                .andExpect(jsonPath("$.data.isWishlisted").value(true));
    }

    @Test
    @DisplayName("GET /api/wishlist - Successfully retrieves enriched wishlist")
    void testGetCustomerWishlistMvc() throws Exception {
        WishlistProductItemResponse item = WishlistProductItemResponse.builder()
                .productPublicId(PRODUCT_ID)
                .productName("Organic Tomato")
                .productSlug("organic-tomato")
                .price(BigDecimal.valueOf(40.00))
                .unit("KG")
                .availabilityStatus("AVAILABLE")
                .isAvailable(true)
                .addedAt(Instant.now())
                .build();

        WishlistResponse response = WishlistResponse.builder()
                .wishlistPublicId("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
                .customerPublicId(CUSTOMER_ID)
                .totalItems(1)
                .items(List.of(item))
                .build();

        when(wishlistService.getCustomerWishlist(CUSTOMER_ID)).thenReturn(response);

        wishlistMockMvc.perform(get("/api/wishlist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].productName").value("Organic Tomato"))
                .andExpect(jsonPath("$.data.items[0].price").value(40.00));
    }

    @Test
    @DisplayName("DELETE /api/wishlist/items/{productId} - Successfully removes product")
    void testRemoveProductMvc() throws Exception {
        when(wishlistService.removeProductFromWishlist(CUSTOMER_ID, PRODUCT_ID)).thenReturn(true);

        wishlistMockMvc.perform(delete("/api/wishlist/items/" + PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Product removed from wishlist successfully"));
    }

    @Test
    @DisplayName("DELETE /api/wishlist - Successfully clears entire customer wishlist")
    void testClearWishlistMvc() throws Exception {
        doNothing().when(wishlistService).clearCustomerWishlist(CUSTOMER_ID);

        wishlistMockMvc.perform(delete("/api/wishlist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Wishlist cleared successfully"));
    }

    @Test
    @DisplayName("GET /api/wishlist/count - Returns wishlist item count")
    void testGetWishlistCountMvc() throws Exception {
        WishlistCountResponse response = WishlistCountResponse.builder().totalItems(7).build();
        when(wishlistService.getWishlistCount(CUSTOMER_ID)).thenReturn(response);

        wishlistMockMvc.perform(get("/api/wishlist/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalItems").value(7));
    }

    @Test
    @DisplayName("GET /api/wishlist/items/{productId}/exists - Returns wishlist existence status")
    void testIsProductWishlistedMvc() throws Exception {
        WishlistStatusResponse response = WishlistStatusResponse.builder()
                .productPublicId(PRODUCT_ID)
                .isWishlisted(true)
                .build();

        when(wishlistService.isProductWishlisted(CUSTOMER_ID, PRODUCT_ID)).thenReturn(response);

        wishlistMockMvc.perform(get("/api/wishlist/items/" + PRODUCT_ID + "/exists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isWishlisted").value(true));
    }

    @Test
    @DisplayName("POST /api/wishlist/items/status - Bulk status check")
    void testBulkStatusMvc() throws Exception {
        WishlistBulkStatusRequest request = WishlistBulkStatusRequest.builder()
                .productPublicIds(List.of(PRODUCT_ID))
                .build();

        WishlistBulkStatusResponse response = WishlistBulkStatusResponse.builder()
                .customerPublicId(CUSTOMER_ID)
                .statusMap(Map.of(PRODUCT_ID, true))
                .items(List.of(WishlistStatusResponse.builder().productPublicId(PRODUCT_ID).isWishlisted(true).build()))
                .build();

        when(wishlistService.checkBulkStatus(eq(CUSTOMER_ID), anyList())).thenReturn(response);

        wishlistMockMvc.perform(post("/api/wishlist/items/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.statusMap['" + PRODUCT_ID + "']").value(true));
    }

    @Test
    @DisplayName("Internal S2S GET /api/internal/wishlist/{customerId} - Retrieves customer wishlist")
    void testInternalGetWishlistMvc() throws Exception {
        WishlistResponse response = WishlistResponse.builder()
                .customerPublicId(CUSTOMER_ID)
                .totalItems(0)
                .items(Collections.emptyList())
                .build();

        when(wishlistService.getCustomerWishlist(CUSTOMER_ID)).thenReturn(response);

        internalMockMvc.perform(get("/api/internal/wishlist/" + CUSTOMER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.customerPublicId").value(CUSTOMER_ID));
    }

    @Test
    @DisplayName("Unauthenticated request should be rejected when customer identity is missing")
    void testUnauthenticatedCustomerRejected() throws Exception {
        SecurityContextHolder.clearContext();

        AddToWishlistRequest request = AddToWishlistRequest.builder()
                .productPublicId(PRODUCT_ID)
                .build();

        wishlistMockMvc.perform(post("/api/wishlist/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
