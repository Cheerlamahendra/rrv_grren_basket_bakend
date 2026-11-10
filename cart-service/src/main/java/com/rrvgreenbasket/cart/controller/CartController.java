
package com.rrvgreenbasket.cart.controller;
import com.rrvgreenbasket.cart.dto.request.AddCartItemRequest;
import com.rrvgreenbasket.cart.dto.response.CartResponse;
import com.rrvgreenbasket.cart.security.UserPrincipal;
import com.rrvgreenbasket.cart.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import com.rrvgreenbasket.cart.dto.request.UpdateCartItemRequest;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<CartResponse> getMyCart(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        CartResponse response = cartService.getMyCart(
                userPrincipal.getUserId()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody AddCartItemRequest request) {

        CartResponse response = cartService.addItem(
                userPrincipal.getUserId(),
                request
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    @PutMapping("/items/{cartItemPublicId}")
    public ResponseEntity<CartResponse> updateItem(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String cartItemPublicId,
            @Valid @RequestBody UpdateCartItemRequest request
    ) {

        CartResponse response = cartService.updateItem(
                userPrincipal.getUserId(),
                cartItemPublicId,
                request
        );

        return ResponseEntity.ok(response);
    }
    @DeleteMapping("/items/{cartItemPublicId}")
    public ResponseEntity<CartResponse> removeItem(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String cartItemPublicId
    ) {

        CartResponse response = cartService.removeItem(
                userPrincipal.getUserId(),
                cartItemPublicId
        );

        return ResponseEntity.ok(response);
    }
    @DeleteMapping("/items")
    public ResponseEntity<CartResponse> clearCart(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {

        CartResponse response = cartService.clearCart(
                userPrincipal.getUserId()
        );

        return ResponseEntity.ok(response);
    }
}
