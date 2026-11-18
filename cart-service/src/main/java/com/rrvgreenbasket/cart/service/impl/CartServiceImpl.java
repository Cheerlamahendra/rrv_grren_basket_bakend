package com.rrvgreenbasket.cart.service.impl;

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
import com.rrvgreenbasket.cart.service.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CartResponseMapper cartResponseMapper;
    private final ProductServiceClient productServiceClient;
    private final InventoryServiceClient inventoryServiceClient;
    private final CartExpirationProperties expirationProperties;
    private final PlatformTransactionManager transactionManager;

    private static final Set<String> SUPPORTED_UNITS = Set.of(
            "KG", "GRAM", "PIECE", "BUNCH", "PACK", "DOZEN", "LITRE", "ML"
    );

    @Override
    public CartResponse getMyCart(String customerPublicId) {
        log.debug("Retrieving cart for customer: {}", customerPublicId);

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        Cart cart = transactionTemplate.execute(status -> {
            Cart c = cartRepository.findByCustomerPublicIdAndStatus(customerPublicId, CartStatus.ACTIVE)
                    .orElseGet(() -> createNewCartInternal(customerPublicId));
            if (c != null && c.getItems() != null) {
                c.getItems().size();
            }
            return c;
        });

        if (cart == null || cart.getItems().isEmpty()) {
            return cartResponseMapper.toResponse(cart);
        }

        // Perform live product price and inventory refresh outside DB transaction
        Map<String, ProductDetailResponse> liveProductMap = new HashMap<>();
        Map<String, StockAvailabilityResponse> liveStockMap = new HashMap<>();

        for (CartItem item : cart.getItems()) {
            String productPublicId = item.getProductPublicId();
            try {
                ProductDetailResponse product = productServiceClient.getProductByPublicId(productPublicId);
                liveProductMap.put(productPublicId, product);
            } catch (Exception ex) {
                log.warn("Could not refresh live product details for {}: {}", productPublicId, ex.getMessage());
            }

            try {
                StockAvailabilityResponse stock = inventoryServiceClient.checkStockAvailability(productPublicId, item.getQuantity());
                liveStockMap.put(productPublicId, stock);
            } catch (Exception ex) {
                log.warn("Could not refresh live inventory availability for {}: {}", productPublicId, ex.getMessage());
            }
        }

        return cartResponseMapper.toResponse(cart, liveProductMap, liveStockMap);
    }

    @Override
    public CartResponse addItem(String customerPublicId, AddCartItemRequest request) {
        String productPublicId = request.getProductPublicId();
        BigDecimal requestedQuantity = request.getEffectiveQuantity();

        if (requestedQuantity == null || requestedQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }

        // 1. Fetch and validate product details from Product Service (OUTSIDE DB transaction)
        ProductDetailResponse product = productServiceClient.getProductByPublicId(productPublicId);

        if (!"ACTIVE".equalsIgnoreCase(product.status())) {
            throw new ProductUnavailableException("Product '" + product.name() + "' is not active");
        }
        if (Boolean.FALSE.equals(product.adminEnabled())) {
            throw new ProductUnavailableException("Product '" + product.name() + "' is currently disabled");
        }
        if (Boolean.FALSE.equals(product.available())) {
            throw new ProductUnavailableException("Product '" + product.name() + "' is currently unavailable");
        }
        if (product.pricePerKg() == null || product.pricePerKg().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Product has an invalid or non-positive price");
        }

        // Validate unit of measure
        String targetUnit = request.getUnitOfMeasure();
        if (targetUnit == null || targetUnit.isBlank()) {
            targetUnit = product.quantityUnit() != null ? product.quantityUnit() : "KG";
        }
        targetUnit = targetUnit.trim().toUpperCase();

        if (!SUPPORTED_UNITS.contains(targetUnit)) {
            throw new IllegalArgumentException("Unsupported unit of measure: '" + targetUnit + "'. Supported: " + SUPPORTED_UNITS);
        }

        if (product.quantityUnit() != null && !product.quantityUnit().equalsIgnoreCase(targetUnit)) {
            throw new IllegalArgumentException("Requested unit '" + targetUnit + "' does not match product unit '" + product.quantityUnit() + "'");
        }

        // 2. Validate product availability and inventory stock (OUTSIDE DB transaction)
        ProductAvailabilityResponse productAvailability =
                productServiceClient.checkProductAvailability(productPublicId, requestedQuantity);

        if (!Boolean.TRUE.equals(productAvailability.available())) {
            throw new ProductUnavailableException(
                    productAvailability.reason() != null ? productAvailability.reason() : "Product is not available for requested quantity"
            );
        }

        StockAvailabilityResponse stock =
                inventoryServiceClient.checkStockAvailability(productPublicId, requestedQuantity);

        if (!Boolean.TRUE.equals(stock.sufficient()) || !Boolean.TRUE.equals(stock.inStock())) {
            throw new InsufficientStockException("Insufficient stock available for requested quantity of " + product.name());
        }

        // 3. Short, atomic DB transaction for persistence
        final String resolvedUnit = targetUnit;
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(status -> {
            Cart cart = cartRepository.findByCustomerPublicIdAndStatus(customerPublicId, CartStatus.ACTIVE)
                    .orElseGet(() -> createNewCartInternal(customerPublicId));

            // Check if item already exists with matching product and unit
            CartItem existingItem = cartItemRepository
                    .findByCartIdAndProductPublicIdAndUnitOfMeasure(cart.getId(), productPublicId, resolvedUnit)
                    .orElse(null);

            if (existingItem != null) {
                BigDecimal updatedQuantity = existingItem.getQuantity().add(requestedQuantity);
                existingItem.setQuantity(updatedQuantity);
                existingItem.setProductNameSnapshot(product.name());
                existingItem.setProductImageUrlSnapshot(product.imageUrl());
                existingItem.setPricePerKgSnapshot(product.pricePerKg());
            } else {
                CartItem newItem = new CartItem();
                newItem.setProductPublicId(product.publicId());
                newItem.setProductNameSnapshot(product.name());
                newItem.setProductImageUrlSnapshot(product.imageUrl());
                newItem.setQuantity(requestedQuantity);
                newItem.setUnitOfMeasure(resolvedUnit);
                newItem.setPricePerKgSnapshot(product.pricePerKg());
                cart.addItem(newItem);
            }

            refreshCartExpiration(cart);
            Cart savedCart = cartRepository.save(cart);
            if (savedCart.getItems() != null) {
                savedCart.getItems().size();
            }
            return cartResponseMapper.toResponse(savedCart);
        });
    }

    @Override
    public CartResponse updateItem(String customerPublicId, String cartItemPublicId, UpdateCartItemRequest request) {
        BigDecimal requestedQuantity = request.getEffectiveQuantity();
        if (requestedQuantity == null || requestedQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }

        // 1. Read cart item details (OUTSIDE long transaction)
        TransactionTemplate readTemplate = new TransactionTemplate(transactionManager);
        readTemplate.setReadOnly(true);
        CartItem readOnlyItem = readTemplate.execute(status -> {
            Cart cart = cartRepository.findByCustomerPublicIdAndStatus(customerPublicId, CartStatus.ACTIVE)
                    .orElseThrow(() -> new CartNotFoundException("Active cart not found"));
            return cartItemRepository.findByPublicIdAndCartId(cartItemPublicId, cart.getId())
                    .orElseThrow(() -> new CartItemNotFoundException("Cart item not found: " + cartItemPublicId));
        });

        String productPublicId = readOnlyItem.getProductPublicId();

        // 2. Perform external validations (OUTSIDE DB transaction)
        ProductDetailResponse product = productServiceClient.getProductByPublicId(productPublicId);

        if (!"ACTIVE".equalsIgnoreCase(product.status())) {
            throw new ProductUnavailableException("Product '" + product.name() + "' is not active");
        }
        if (Boolean.FALSE.equals(product.adminEnabled()) || Boolean.FALSE.equals(product.available())) {
            throw new ProductUnavailableException("Product '" + product.name() + "' is currently unavailable");
        }

        ProductAvailabilityResponse productAvailability =
                productServiceClient.checkProductAvailability(productPublicId, requestedQuantity);

        if (!Boolean.TRUE.equals(productAvailability.available())) {
            throw new ProductUnavailableException(
                    productAvailability.reason() != null ? productAvailability.reason() : "Product is unavailable for requested quantity"
            );
        }

        StockAvailabilityResponse stock =
                inventoryServiceClient.checkStockAvailability(productPublicId, requestedQuantity);

        if (!Boolean.TRUE.equals(stock.sufficient()) || !Boolean.TRUE.equals(stock.inStock())) {
            throw new InsufficientStockException("Insufficient stock available for requested quantity of " + product.name());
        }

        // 3. Short atomic update transaction
        TransactionTemplate writeTemplate = new TransactionTemplate(transactionManager);
        return writeTemplate.execute(status -> {
            Cart cart = cartRepository.findByCustomerPublicIdAndStatus(customerPublicId, CartStatus.ACTIVE)
                    .orElseThrow(() -> new CartNotFoundException("Active cart not found"));

            CartItem itemToUpdate = cartItemRepository.findByPublicIdAndCartId(cartItemPublicId, cart.getId())
                    .orElseThrow(() -> new CartItemNotFoundException("Cart item not found: " + cartItemPublicId));

            itemToUpdate.setQuantity(requestedQuantity);
            itemToUpdate.setProductNameSnapshot(product.name());
            itemToUpdate.setProductImageUrlSnapshot(product.imageUrl());
            itemToUpdate.setPricePerKgSnapshot(product.pricePerKg());

            refreshCartExpiration(cart);
            Cart saved = cartRepository.save(cart);
            if (saved.getItems() != null) {
                saved.getItems().size();
            }
            return cartResponseMapper.toResponse(saved);
        });
    }

    @Override
    public CartResponse removeItem(String customerPublicId, String cartItemPublicId) {
        TransactionTemplate writeTemplate = new TransactionTemplate(transactionManager);
        return writeTemplate.execute(status -> {
            Cart cart = cartRepository.findByCustomerPublicIdAndStatus(customerPublicId, CartStatus.ACTIVE)
                    .orElseThrow(() -> new CartNotFoundException("Active cart not found"));

            CartItem itemToRemove = cartItemRepository.findByPublicIdAndCartId(cartItemPublicId, cart.getId())
                    .orElseThrow(() -> new CartItemNotFoundException("Cart item not found: " + cartItemPublicId));

            cart.removeItem(itemToRemove);
            refreshCartExpiration(cart);
            Cart saved = cartRepository.save(cart);
            if (saved.getItems() != null) {
                saved.getItems().size();
            }
            return cartResponseMapper.toResponse(saved);
        });
    }

    @Override
    public CartResponse clearCart(String customerPublicId) {
        TransactionTemplate writeTemplate = new TransactionTemplate(transactionManager);
        return writeTemplate.execute(status -> {
            Cart cart = cartRepository.findByCustomerPublicIdAndStatus(customerPublicId, CartStatus.ACTIVE)
                    .orElseThrow(() -> new CartNotFoundException("Active cart not found"));

            cart.getItems().clear();
            refreshCartExpiration(cart);
            Cart saved = cartRepository.save(cart);
            if (saved.getItems() != null) {
                saved.getItems().size();
            }
            return cartResponseMapper.toResponse(saved);
        });
    }

    @Override
    public CartValidationResponse validateCart(String customerPublicId) {
        log.info("Validating cart for customer: {}", customerPublicId);

        TransactionTemplate readTemplate = new TransactionTemplate(transactionManager);
        readTemplate.setReadOnly(true);
        Cart cart = readTemplate.execute(status -> {
            Cart c = cartRepository.findByCustomerPublicIdAndStatus(customerPublicId, CartStatus.ACTIVE).orElse(null);
            if (c != null && c.getItems() != null) {
                c.getItems().size();
            }
            return c;
        });

        if (cart == null || cart.getItems().isEmpty()) {
            return CartValidationResponse.builder()
                    .cartPublicId(cart != null ? cart.getPublicId() : null)
                    .valid(false)
                    .items(Collections.emptyList())
                    .totalItems(0)
                    .previousSubtotal(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    .currentSubtotal(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    .currency(cart != null && cart.getCurrency() != null ? cart.getCurrency() : "INR")
                    .priceChanged(false)
                    .unavailableItemCount(0)
                    .insufficientStockCount(0)
                    .validationMessages(List.of("Cart is empty"))
                    .build();
        }

        List<CartItemValidationResponse> validationItems = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        int unavailableCount = 0;
        int insufficientCount = 0;
        BigDecimal previousSubtotal = BigDecimal.ZERO;
        BigDecimal currentSubtotal = BigDecimal.ZERO;

        for (CartItem item : cart.getItems()) {
            String productPublicId = item.getProductPublicId();
            BigDecimal quantity = item.getQuantity();
            BigDecimal snapshotPrice = item.getPricePerKgSnapshot().setScale(2, RoundingMode.HALF_UP);
            BigDecimal itemPreviousSubtotal = snapshotPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
            previousSubtotal = previousSubtotal.add(itemPreviousSubtotal);

            ProductDetailResponse product = null;
            StockAvailabilityResponse stock = null;
            CartItemValidationStatus itemStatus = CartItemValidationStatus.VALID;
            String itemMessage = "Item is valid and in stock";
            BigDecimal currentPrice = snapshotPrice;

            // 1. Check Product details
            try {
                product = productServiceClient.getProductByPublicId(productPublicId);

                if (!"ACTIVE".equalsIgnoreCase(product.status()) || Boolean.FALSE.equals(product.adminEnabled())) {
                    itemStatus = CartItemValidationStatus.PRODUCT_UNAVAILABLE;
                    itemMessage = "Product '" + product.name() + "' is currently inactive or disabled";
                    unavailableCount++;
                } else if (Boolean.FALSE.equals(product.available())) {
                    itemStatus = CartItemValidationStatus.PRODUCT_UNAVAILABLE;
                    itemMessage = "Product '" + product.name() + "' is marked unavailable";
                    unavailableCount++;
                } else if (product.quantityUnit() != null && !product.quantityUnit().equalsIgnoreCase(item.getUnitOfMeasure())) {
                    itemStatus = CartItemValidationStatus.INVALID_UNIT;
                    itemMessage = "Product unit has changed from '" + item.getUnitOfMeasure() + "' to '" + product.quantityUnit() + "'";
                    unavailableCount++;
                } else if (product.pricePerKg() != null) {
                    currentPrice = product.pricePerKg().setScale(2, RoundingMode.HALF_UP);
                    if (currentPrice.compareTo(snapshotPrice) != 0) {
                        itemStatus = CartItemValidationStatus.PRICE_CHANGED;
                        itemMessage = String.format("Price changed from INR %.2f to INR %.2f", snapshotPrice, currentPrice);
                    }
                }
            } catch (ProductNotFoundException ex) {
                itemStatus = CartItemValidationStatus.PRODUCT_UNAVAILABLE;
                itemMessage = "Product no longer exists in catalog";
                unavailableCount++;
            } catch (Exception ex) {
                log.warn("Downstream error validating product {}: {}", productPublicId, ex.getMessage());
                itemStatus = CartItemValidationStatus.PRODUCT_UNAVAILABLE;
                itemMessage = "Unable to verify product details with catalog service";
                unavailableCount++;
            }

            // 2. Check Inventory Stock
            try {
                stock = inventoryServiceClient.checkStockAvailability(productPublicId, quantity);

                if (!Boolean.TRUE.equals(stock.inStock())) {
                    itemStatus = CartItemValidationStatus.OUT_OF_STOCK;
                    itemMessage = "Product is out of stock";
                    insufficientCount++;
                } else if (!Boolean.TRUE.equals(stock.sufficient())
                        || (stock.availableQuantity() != null && stock.availableQuantity().compareTo(quantity) < 0)) {
                    itemStatus = CartItemValidationStatus.INSUFFICIENT_STOCK;
                    itemMessage = String.format("Insufficient stock: %.3f %s available, %.3f requested",
                            stock.availableQuantity() != null ? stock.availableQuantity() : BigDecimal.ZERO,
                            item.getUnitOfMeasure(),
                            quantity);
                    insufficientCount++;
                }
            } catch (Exception ex) {
                log.warn("Downstream error checking stock for {}: {}", productPublicId, ex.getMessage());
                itemStatus = CartItemValidationStatus.OUT_OF_STOCK;
                itemMessage = "Unable to verify inventory availability";
                insufficientCount++;
            }

            BigDecimal itemCurrentSubtotal = currentPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
            currentSubtotal = currentSubtotal.add(itemCurrentSubtotal);

            boolean priceChanged = snapshotPrice.compareTo(currentPrice) != 0;
            if (priceChanged && !messages.contains(itemMessage) && itemStatus == CartItemValidationStatus.PRICE_CHANGED) {
                messages.add(String.format("'%s': %s", item.getProductNameSnapshot(), itemMessage));
            } else if (itemStatus != CartItemValidationStatus.VALID) {
                messages.add(String.format("'%s': %s", item.getProductNameSnapshot(), itemMessage));
            }

            validationItems.add(CartItemValidationResponse.builder()
                    .itemPublicId(item.getPublicId())
                    .productPublicId(productPublicId)
                    .productName(product != null && product.name() != null ? product.name() : item.getProductNameSnapshot())
                    .quantity(quantity)
                    .unitOfMeasure(item.getUnitOfMeasure())
                    .previousUnitPrice(snapshotPrice)
                    .currentUnitPrice(currentPrice)
                    .priceChanged(priceChanged)
                    .availableQuantity(stock != null ? stock.availableQuantity() : null)
                    .inStock(stock != null && Boolean.TRUE.equals(stock.inStock()))
                    .sufficient(stock != null && Boolean.TRUE.equals(stock.sufficient()))
                    .itemSubtotal(itemCurrentSubtotal)
                    .status(itemStatus)
                    .message(itemMessage)
                    .build());
        }

        boolean cartValid = unavailableCount == 0 && insufficientCount == 0;
        boolean cartPriceChanged = previousSubtotal.compareTo(currentSubtotal) != 0;

        if (cartValid && cartPriceChanged) {
            messages.add(0, "Cart items are in stock, but unit prices have updated. Please review updated subtotal before checkout.");
        } else if (cartValid) {
            messages.add(0, "All cart items are verified and available for checkout.");
        } else {
            messages.add(0, "Some items in your cart require attention before checkout.");
        }

        return CartValidationResponse.builder()
                .cartPublicId(cart.getPublicId())
                .valid(cartValid)
                .items(validationItems)
                .totalItems(validationItems.size())
                .previousSubtotal(previousSubtotal.setScale(2, RoundingMode.HALF_UP))
                .currentSubtotal(currentSubtotal.setScale(2, RoundingMode.HALF_UP))
                .currency(cart.getCurrency() != null ? cart.getCurrency() : "INR")
                .priceChanged(cartPriceChanged)
                .unavailableItemCount(unavailableCount)
                .insufficientStockCount(insufficientCount)
                .validationMessages(messages)
                .build();
    }

    @Override
    public int expireAbandonedCarts() {
        LocalDateTime now = LocalDateTime.now();
        int batchSize = expirationProperties.getBatchSize() > 0 ? expirationProperties.getBatchSize() : 100;

        PageRequest pageRequest = PageRequest.of(0, batchSize);
        Page<Cart> expiredPage = cartRepository.findByStatusAndExpiresAtBefore(CartStatus.ACTIVE, now, pageRequest);

        if (expiredPage.isEmpty()) {
            return 0;
        }

        List<Long> cartIdsToExpire = expiredPage.getContent().stream()
                .map(Cart::getId)
                .toList();

        TransactionTemplate writeTemplate = new TransactionTemplate(transactionManager);
        int updated = writeTemplate.execute(status ->
                cartRepository.updateStatusForCartIds(cartIdsToExpire, CartStatus.EXPIRED)
        );

        log.info("Batch expired {} abandoned carts (threshold={})", updated, now);
        return updated;
    }

    private Cart createNewCartInternal(String customerPublicId) {
        Cart cart = new Cart();
        cart.setCustomerPublicId(customerPublicId);
        cart.setStatus(CartStatus.ACTIVE);
        cart.setCurrency("INR");
        refreshCartExpiration(cart);
        return cartRepository.save(cart);
    }

    private void refreshCartExpiration(Cart cart) {
        int durationDays = expirationProperties.getDurationDays() > 0 ? expirationProperties.getDurationDays() : 7;
        cart.setExpiresAt(LocalDateTime.now().plusDays(durationDays));
    }
}
