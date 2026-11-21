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
import com.rrvgreenbasket.cart.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CartResponseMapper cartResponseMapper;
    private final ProductServiceClient productServiceClient;

    @Override
    @Transactional
    public CartResponse getMyCart(String customerPublicId) {

        Cart cart = cartRepository
                .findByCustomerPublicIdAndStatus(
                        customerPublicId,
                        CartStatus.ACTIVE
                )
                .orElseGet(() -> createNewCart(customerPublicId));

        return cartResponseMapper.toResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse addItem(
            String customerPublicId,
            AddCartItemRequest request
    ) {

        String productPublicId = request.productPublicId();
        BigDecimal requestedQuantity = request.quantityKg();

        // 1. Fetch product details
        ProductDetailResponse product =
                productServiceClient.getProductByPublicId(productPublicId);

        // 2. Verify that the product is active and available
        if (!Boolean.TRUE.equals(product.available())
                || !"ACTIVE".equalsIgnoreCase(product.status())) {

            throw new ProductUnavailableException(
                    "This product is currently unavailable"
            );
        }

        // 3. Check requested quantity using Product Service availability
        ProductAvailabilityResponse productAvailability =
                productServiceClient.checkProductAvailability(
                        productPublicId,
                        requestedQuantity
                );

        if (!Boolean.TRUE.equals(productAvailability.available())) {

            throw new ProductUnavailableException(
                    productAvailability.reason() != null
                            ? productAvailability.reason()
                            : "Product is not available for the requested quantity"
            );
        }

        // 4. Validate that product price is valid
        if (product.pricePerKg() == null
                || product.pricePerKg().compareTo(BigDecimal.ZERO) <= 0) {

            throw new IllegalStateException(
                    "Invalid product price"
            );
        }

        // 5. Get the unit of measure from ProductDetailResponse.quantityUnit()
        // 6. Validate the unit of measure is not null or blank
        String unitOfMeasure = product.quantityUnit();
        if (unitOfMeasure == null || unitOfMeasure.isBlank()) {

            throw new IllegalStateException(
                    "Product unit of measure is missing"
            );
        }

        // 7. Find or create the customer's active cart
        Cart cart = cartRepository
                .findByCustomerPublicIdAndStatus(
                        customerPublicId,
                        CartStatus.ACTIVE
                )
                .orElseGet(() -> createNewCart(customerPublicId));

        // 8. If the same product already exists, increase its quantity
        CartItem item = cartItemRepository
                .findByCartIdAndProductPublicIdAndUnitOfMeasure(
                        cart.getId(),
                        productPublicId,
                        unitOfMeasure
                )
                .orElse(null);

        if (item != null) {

            BigDecimal updatedQuantity =
                    item.getQuantity().add(requestedQuantity);

            // 9. Recheck the combined quantity using Product Service availability
            ProductAvailabilityResponse updatedProductAvailability =
                    productServiceClient.checkProductAvailability(
                            productPublicId,
                            updatedQuantity
                    );

            if (!Boolean.TRUE.equals(updatedProductAvailability.available())) {

                throw new ProductUnavailableException(
                        updatedProductAvailability.reason() != null
                                ? updatedProductAvailability.reason()
                                : "Product is not available for the updated cart quantity"
                );
            }

            item.setQuantity(updatedQuantity);
            item.setProductNameSnapshot(product.name());
            item.setProductImageUrlSnapshot(product.imageUrl());
            item.setPricePerKgSnapshot(product.pricePerKg());

        } else {

            item = new CartItem();
            item.setProductPublicId(product.publicId());
            item.setProductNameSnapshot(product.name());
            item.setProductImageUrlSnapshot(product.imageUrl());
            item.setQuantity(requestedQuantity);
            item.setUnitOfMeasure(unitOfMeasure);
            item.setPricePerKgSnapshot(product.pricePerKg());

            cart.addItem(item);
        }

        // 10. Save the updated cart
        cartRepository.save(cart);

        return cartResponseMapper.toResponse(cart);
    }

    private Cart createNewCart(String customerPublicId) {

        Cart cart = new Cart();
        cart.setCustomerPublicId(customerPublicId);
        cart.setStatus(CartStatus.ACTIVE);

        return cartRepository.save(cart);
    }

    @Override
    @Transactional
    public CartResponse updateItem(
            String customerPublicId,
            String cartItemPublicId,
            UpdateCartItemRequest request
    ) {

        BigDecimal requestedQuantity = request.quantityKg();

        // 1. Find the authenticated customer's active cart
        Cart cart = cartRepository
                .findByCustomerPublicIdAndStatus(
                        customerPublicId,
                        CartStatus.ACTIVE
                )
                .orElseThrow(() ->
                        new CartNotFoundException("Active cart not found")
                );

        // 2. Verify that the requested cart item belongs to that customer
        CartItem item = cartItemRepository
                .findByPublicIdAndCartId(
                        cartItemPublicId,
                        cart.getId()
                )
                .orElseThrow(() ->
                        new CartItemNotFoundException("Cart item not found")
                );

        String productPublicId = item.getProductPublicId();

        // 3. Fetch the latest product details from Product Service
        ProductDetailResponse product =
                productServiceClient.getProductByPublicId(productPublicId);

        // 4. Verify product availability and active status
        if (!Boolean.TRUE.equals(product.available())
                || !"ACTIVE".equalsIgnoreCase(product.status())) {

            throw new ProductUnavailableException(
                    "This product is currently unavailable"
            );
        }

        // 5. Check the requested quantity using Product Service
        ProductAvailabilityResponse productAvailability =
                productServiceClient.checkProductAvailability(
                        productPublicId,
                        requestedQuantity
                );

        if (!Boolean.TRUE.equals(productAvailability.available())) {

            throw new ProductUnavailableException(
                    productAvailability.reason() != null
                            ? productAvailability.reason()
                            : "Product is unavailable for the requested quantity"
            );
        }

        // 6. Validate the product's quantityUnit() against the existing cart item's unit
        if (product.quantityUnit() == null
                || !product.quantityUnit().equalsIgnoreCase(
                item.getUnitOfMeasure()
        )) {

            throw new IllegalStateException(
                    "Product unit of measure does not match cart item"
            );
        }

        // 7. Update quantity and product snapshots
        item.setQuantity(requestedQuantity);
        item.setProductNameSnapshot(product.name());
        item.setProductImageUrlSnapshot(product.imageUrl());
        item.setPricePerKgSnapshot(product.pricePerKg());

        // 8. Save and return the updated cart
        cartRepository.save(cart);

        return cartResponseMapper.toResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse removeItem(
            String customerPublicId,
            String cartItemPublicId
    ) {

        // 1. Find customer's active cart
        Cart cart = cartRepository
                .findByCustomerPublicIdAndStatus(
                        customerPublicId,
                        CartStatus.ACTIVE
                )
                .orElseThrow(() ->
                        new CartNotFoundException("Active cart not found")
                );

        // 2. Find item belonging to customer's cart
        CartItem item = cartItemRepository
                .findByPublicIdAndCartId(
                        cartItemPublicId,
                        cart.getId()
                )
                .orElseThrow(() ->
                        new CartItemNotFoundException("Cart item not found")
                );

        // 3. Remove item from cart
        cart.removeItem(item);

        // 4. Save updated cart
        cartRepository.save(cart);

        // 5. Return updated cart
        return cartResponseMapper.toResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse clearCart(String customerPublicId) {

        // 1. Find customer's active cart
        Cart cart = cartRepository
                .findByCustomerPublicIdAndStatus(
                        customerPublicId,
                        CartStatus.ACTIVE
                )
                .orElseThrow(() ->
                        new CartNotFoundException("Active cart not found")
                );

        // 2. Remove all items from cart
        cart.getItems().clear();

        // 3. Save updated cart
        cartRepository.save(cart);

        // 4. Return empty cart response
        return cartResponseMapper.toResponse(cart);
    }
}
