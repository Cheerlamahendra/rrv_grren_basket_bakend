
package com.rrvgreenbasket.cart.service.impl;

import com.rrvgreenbasket.cart.client.InventoryServiceClient;
import com.rrvgreenbasket.cart.client.ProductServiceClient;
import com.rrvgreenbasket.cart.dto.client.ProductAvailabilityResponse;
import com.rrvgreenbasket.cart.dto.client.ProductDetailResponse;
import com.rrvgreenbasket.cart.dto.client.StockAvailabilityResponse;
import com.rrvgreenbasket.cart.dto.request.AddCartItemRequest;
import com.rrvgreenbasket.cart.dto.request.UpdateCartItemRequest;
import com.rrvgreenbasket.cart.dto.response.CartResponse;
import com.rrvgreenbasket.cart.entity.Cart;
import com.rrvgreenbasket.cart.entity.CartItem;
import com.rrvgreenbasket.cart.entity.CartStatus;
import com.rrvgreenbasket.cart.exception.CartItemNotFoundException;
import com.rrvgreenbasket.cart.exception.CartNotFoundException;
import com.rrvgreenbasket.cart.exception.InsufficientStockException;
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
    private final InventoryServiceClient inventoryServiceClient;

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

        if (!Boolean.TRUE.equals(product.available())
                || !"ACTIVE".equalsIgnoreCase(product.status())) {

            throw new ProductUnavailableException(
                    "This product is currently unavailable"
            );
        }

        // 2. Check product availability
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

        // 3. Check inventory availability
        StockAvailabilityResponse stock =
                inventoryServiceClient.checkStockAvailability(
                        productPublicId,
                        requestedQuantity
                );

        if (!Boolean.TRUE.equals(stock.sufficient())) {

            throw new InsufficientStockException(
                    "Insufficient stock for the requested quantity"
            );
        }

        // 4. Validate price and unit
        if (product.pricePerKg() == null
                || product.pricePerKg().compareTo(BigDecimal.ZERO) <= 0) {

            throw new IllegalStateException(
                    "Invalid product price"
            );
        }

        if (stock.unitOfMeasure() == null
                || stock.unitOfMeasure().isBlank()) {

            throw new IllegalStateException(
                    "Product unit of measure is missing"
            );
        }

        // 5. Find or create active cart
        Cart cart = cartRepository
                .findByCustomerPublicIdAndStatus(
                        customerPublicId,
                        CartStatus.ACTIVE
                )
                .orElseGet(() -> createNewCart(customerPublicId));

        // 6. Find existing item with same product and unit
        CartItem item = cartItemRepository
                .findByCartIdAndProductPublicIdAndUnitOfMeasure(
                        cart.getId(),
                        productPublicId,
                        stock.unitOfMeasure()
                )
                .orElse(null);

        if (item != null) {

            BigDecimal updatedQuantity =
                    item.getQuantity().add(requestedQuantity);

            // Recheck availability for combined quantity
            ProductAvailabilityResponse updatedProductAvailability =
                    productServiceClient.checkProductAvailability(
                            productPublicId,
                            updatedQuantity
                    );

            StockAvailabilityResponse updatedStock =
                    inventoryServiceClient.checkStockAvailability(
                            productPublicId,
                            updatedQuantity
                    );

            if (!Boolean.TRUE.equals(updatedProductAvailability.available())
                    || !Boolean.TRUE.equals(updatedStock.sufficient())) {

                throw new InsufficientStockException(
                        "Insufficient availability for the updated cart quantity"
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
            item.setUnitOfMeasure(stock.unitOfMeasure());
            item.setPricePerKgSnapshot(product.pricePerKg());

            cart.addItem(item);
        }

        cartRepository.save(cart);

        // 7. Return updated cart
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

        // 1. Find customer's active cart
        Cart cart = cartRepository
                .findByCustomerPublicIdAndStatus(
                        customerPublicId,
                        CartStatus.ACTIVE
                )
                .orElseThrow(() ->
                        new CartNotFoundException("Active cart not found")
                );

        // 2. Find item only inside this customer's cart
        CartItem item = cartItemRepository
                .findByPublicIdAndCartId(
                        cartItemPublicId,
                        cart.getId()
                )
                .orElseThrow(() ->
                        new CartItemNotFoundException("Cart item not found")
                );

        String productPublicId = item.getProductPublicId();

        // 3. Fetch latest product details
        ProductDetailResponse product =
                productServiceClient.getProductByPublicId(productPublicId);

        if (!Boolean.TRUE.equals(product.available())
                || !"ACTIVE".equalsIgnoreCase(product.status())) {

            throw new ProductUnavailableException(
                    "This product is currently unavailable"
            );
        }

        // 4. Check product availability for new quantity
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

        // 5. Check inventory for new quantity
        StockAvailabilityResponse stock =
                inventoryServiceClient.checkStockAvailability(
                        productPublicId,
                        requestedQuantity
                );

        if (!Boolean.TRUE.equals(stock.sufficient())) {

            throw new InsufficientStockException(
                    "Insufficient stock for the requested quantity"
            );
        }

        // 6. Validate unit of measure
        if (stock.unitOfMeasure() == null
                || !stock.unitOfMeasure().equalsIgnoreCase(
                item.getUnitOfMeasure()
        )) {

            throw new IllegalStateException(
                    "Product unit of measure does not match cart item"
            );
        }

        // 7. Update existing cart item
        item.setQuantity(requestedQuantity);
        item.setProductNameSnapshot(product.name());
        item.setProductImageUrlSnapshot(product.imageUrl());
        item.setPricePerKgSnapshot(product.pricePerKg());

        cartRepository.save(cart);

        // 8. Return updated cart
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
