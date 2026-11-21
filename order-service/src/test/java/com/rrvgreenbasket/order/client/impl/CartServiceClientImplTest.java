package com.rrvgreenbasket.order.client.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.order.client.dto.CartClientResponse;
import com.rrvgreenbasket.order.client.dto.CartValidationClientResponse;
import com.rrvgreenbasket.order.exception.OrderValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class CartServiceClientImplTest {

    private static final String TEST_SECRET = "RQpmB5hGO2NsLNE7OnwXo5NfNtEJj+j+461g0oEeGunJPZ4iYQEGr+uk5M3hAfgDg26IDtmA9N2RJbFXkCLrqQ==";
    private static final String ISSUER = "rrv-gateway";
    private static final String CUSTOMER_ID = "ab5d2528-78c1-40bc-be23-cc869816db8a";

    private MockRestServiceServer mockServer;
    private CartServiceClientImpl cartServiceClient;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl("http://localhost:8086");
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = restClientBuilder.build();
        cartServiceClient = new CartServiceClientImpl(restClient, TEST_SECRET, ISSUER, objectMapper);
    }

    @Test
    @DisplayName("getCustomerCart successfully parses direct CartResponse from cart-service")
    void getCustomerCart_directCartResponse_success() {
        String cartJson = """
                {
                    "publicId": "cab02af6-3a4e-4afa-99d6-8618af3d1c44",
                    "status": "ACTIVE",
                    "items": [
                        {
                            "publicId": "item-1",
                            "productPublicId": "prod-1",
                            "productName": "Fresh Coriander Leaves",
                            "productImageUrl": "https://img.com/coriander.jpg",
                            "quantityKg": 1.0,
                            "unitOfMeasure": "KG",
                            "pricePerKg": 20.00,
                            "totalPrice": 20.00
                        }
                    ],
                    "itemCount": 1,
                    "totalAmount": 20.00,
                    "createdAt": "2026-11-20T10:00:00",
                    "updatedAt": "2026-11-20T10:00:00"
                }
                """;

        mockServer.expect(requestTo("http://localhost:8086/api/cart"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Gateway-Assertion", org.hamcrest.Matchers.notNullValue()))
                .andRespond(withSuccess(cartJson, MediaType.APPLICATION_JSON));

        CartClientResponse cart = cartServiceClient.getCustomerCart(CUSTOMER_ID);

        assertThat(cart).isNotNull();
        assertThat(cart.getPublicId()).isEqualTo("cab02af6-3a4e-4afa-99d6-8618af3d1c44");
        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getItems().get(0).getProductName()).isEqualTo("Fresh Coriander Leaves");
        assertThat(cart.getItems().get(0).getQuantityKg()).isEqualByComparingTo(BigDecimal.valueOf(1.0));
        assertThat(cart.getItems().get(0).getPricePerKg()).isEqualByComparingTo(BigDecimal.valueOf(20.00));
        assertThat(cart.getSubtotal()).isEqualByComparingTo(BigDecimal.valueOf(20.00));
        mockServer.verify();
    }

    @Test
    @DisplayName("getCustomerCart successfully parses wrapped ServiceApiResponse format")
    void getCustomerCart_wrappedResponse_success() {
        String wrappedJson = """
                {
                    "success": true,
                    "message": "Cart retrieved",
                    "data": {
                        "publicId": "cab02af6-3a4e-4afa-99d6-8618af3d1c44",
                        "status": "ACTIVE",
                        "items": [
                            {
                                "publicId": "item-1",
                                "productPublicId": "prod-1",
                                "productName": "Tomato",
                                "quantityKg": 2.0,
                                "pricePerKg": 30.00
                            }
                        ],
                        "itemCount": 1,
                        "totalAmount": 60.00
                    }
                }
                """;

        mockServer.expect(requestTo("http://localhost:8086/api/cart"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(wrappedJson, MediaType.APPLICATION_JSON));

        CartClientResponse cart = cartServiceClient.getCustomerCart(CUSTOMER_ID);

        assertThat(cart).isNotNull();
        assertThat(cart.getPublicId()).isEqualTo("cab02af6-3a4e-4afa-99d6-8618af3d1c44");
        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getItems().get(0).getProductName()).isEqualTo("Tomato");
        mockServer.verify();
    }

    @Test
    @DisplayName("getCustomerCart throws OrderValidationException on 404")
    void getCustomerCart_notFound_throwsValidationException() {
        mockServer.expect(requestTo("http://localhost:8086/api/cart"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());

        assertThatThrownBy(() -> cartServiceClient.getCustomerCart(CUSTOMER_ID))
                .isInstanceOf(OrderValidationException.class)
                .hasMessageContaining("does not have an active shopping cart");

        mockServer.verify();
    }

    @Test
    @DisplayName("clearCart calls DELETE /api/cart")
    void clearCart_callsDeleteApiCart() {
        mockServer.expect(requestTo("http://localhost:8086/api/cart"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("X-Gateway-Assertion", org.hamcrest.Matchers.notNullValue()))
                .andRespond(withSuccess());

        cartServiceClient.clearCart(CUSTOMER_ID);

        mockServer.verify();
    }

    @Test
    @DisplayName("validateCart gracefully succeeds when endpoint returns 404")
    void validateCart_notFound_returnsValidFallback() {
        mockServer.expect(requestTo("http://localhost:8086/api/cart/validate"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withResourceNotFound());

        CartValidationClientResponse validation = cartServiceClient.validateCart(CUSTOMER_ID);

        assertThat(validation).isNotNull();
        assertThat(validation.getIsValid()).isTrue();
        mockServer.verify();
    }
}
