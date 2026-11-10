package com.rrvgreenbasket.wishlist.service.impl;

import com.rrvgreenbasket.wishlist.client.ProductServiceClient;
import com.rrvgreenbasket.wishlist.dto.client.ProductDto;
import com.rrvgreenbasket.wishlist.dto.response.*;
import com.rrvgreenbasket.wishlist.entity.Wishlist;
import com.rrvgreenbasket.wishlist.entity.WishlistItem;
import com.rrvgreenbasket.wishlist.exception.InvalidRequestException;
import com.rrvgreenbasket.wishlist.exception.ResourceNotFoundException;
import com.rrvgreenbasket.wishlist.repository.WishlistItemRepository;
import com.rrvgreenbasket.wishlist.repository.WishlistRepository;
import com.rrvgreenbasket.wishlist.service.WishlistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WishlistServiceImpl implements WishlistService {

    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-fA-F-]{36}$");

    private final WishlistRepository wishlistRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final ProductServiceClient productServiceClient;

    @Override
    @Transactional
    public WishlistItemResponse addProductToWishlist(String customerPublicId, String productPublicId) {
        validatePublicId(customerPublicId, "Customer public ID");
        validatePublicId(productPublicId, "Product public ID");

        log.info("Adding product {} to wishlist for customer {}", productPublicId, customerPublicId);

        // 1. Validate product existence and active eligibility via Product Service
        productServiceClient.validateProductForWishlist(productPublicId);

        // 2. Find or create customer wishlist
        Wishlist wishlist = wishlistRepository.findByCustomerPublicId(customerPublicId)
                .orElseGet(() -> {
                    log.info("Creating new wishlist for customer {}", customerPublicId);
                    Wishlist newWishlist = Wishlist.builder()
                            .customerPublicId(customerPublicId)
                            .items(new ArrayList<>())
                            .build();
                    return wishlistRepository.save(newWishlist);
                });

        // 3. Check for existing item (idempotency check)
        boolean alreadyExists = wishlistItemRepository.existsByWishlistIdAndProductPublicId(wishlist.getId(), productPublicId);
        if (alreadyExists) {
            log.info("Product {} is already present in wishlist for customer {}. Returning idempotent success.",
                    productPublicId, customerPublicId);
            return WishlistItemResponse.builder()
                    .productPublicId(productPublicId)
                    .isWishlisted(true)
                    .build();
        }

        // 4. Create and persist new wishlist item
        WishlistItem newItem = WishlistItem.builder()
                .wishlist(wishlist)
                .productPublicId(productPublicId)
                .addedAt(Instant.now())
                .build();

        try {
            wishlistItemRepository.save(newItem);
            wishlist.getItems().add(newItem);
            log.info("Product {} successfully added to wishlist for customer {}", productPublicId, customerPublicId);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent request edge case - handled idempotently
            log.warn("Concurrent duplicate insert detected for customer {} and product {}: {}",
                    customerPublicId, productPublicId, ex.getMessage());
        }

        return WishlistItemResponse.builder()
                .productPublicId(productPublicId)
                .isWishlisted(true)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public WishlistResponse getCustomerWishlist(String customerPublicId) {
        validatePublicId(customerPublicId, "Customer public ID");

        log.debug("Retrieving wishlist for customer {}", customerPublicId);

        // Prevent N+1 queries using JOIN FETCH query
        Optional<Wishlist> wishlistOpt = wishlistRepository.findByCustomerPublicIdWithItems(customerPublicId);

        if (wishlistOpt.isEmpty() || wishlistOpt.get().getItems().isEmpty()) {
            String wishlistPublicId = wishlistOpt.map(Wishlist::getWishlistPublicId).orElse(null);
            return WishlistResponse.builder()
                    .wishlistPublicId(wishlistPublicId)
                    .customerPublicId(customerPublicId)
                    .totalItems(0)
                    .items(Collections.emptyList())
                    .build();
        }

        Wishlist wishlist = wishlistOpt.get();
        List<WishlistItem> items = wishlist.getItems();

        // Enrich items with real-time product details from Product Service
        List<WishlistProductItemResponse> enrichedItems = items.stream()
                .map(item -> {
                    ProductDto productDto = productServiceClient.getProductDetails(item.getProductPublicId());
                    return mapToProductItemResponse(item, productDto);
                })
                .collect(Collectors.toList());

        return WishlistResponse.builder()
                .wishlistPublicId(wishlist.getWishlistPublicId())
                .customerPublicId(customerPublicId)
                .totalItems(enrichedItems.size())
                .items(enrichedItems)
                .build();
    }

    @Override
    @Transactional
    public boolean removeProductFromWishlist(String customerPublicId, String productPublicId) {
        validatePublicId(customerPublicId, "Customer public ID");
        validatePublicId(productPublicId, "Product public ID");

        log.info("Removing product {} from wishlist for customer {}", productPublicId, customerPublicId);

        Wishlist wishlist = wishlistRepository.findByCustomerPublicId(customerPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Wishlist not found for customer: " + customerPublicId));

        boolean exists = wishlistItemRepository.existsByWishlistIdAndProductPublicId(wishlist.getId(), productPublicId);
        if (!exists) {
            log.warn("Product {} not found in wishlist for customer {}", productPublicId, customerPublicId);
            throw new ResourceNotFoundException("Product " + productPublicId + " not found in customer's wishlist");
        }

        wishlistItemRepository.deleteByWishlistIdAndProductPublicId(wishlist.getId(), productPublicId);
        wishlist.removeItem(productPublicId);

        log.info("Product {} removed from wishlist for customer {}", productPublicId, customerPublicId);
        return true;
    }

    @Override
    @Transactional
    public void clearCustomerWishlist(String customerPublicId) {
        validatePublicId(customerPublicId, "Customer public ID");

        log.info("Clearing wishlist for customer {}", customerPublicId);

        Optional<Wishlist> wishlistOpt = wishlistRepository.findByCustomerPublicId(customerPublicId);
        if (wishlistOpt.isEmpty()) {
            log.info("Wishlist does not exist for customer {}. Clear operation completed idempotently.", customerPublicId);
            return;
        }

        Wishlist wishlist = wishlistOpt.get();
        wishlistItemRepository.deleteByWishlistId(wishlist.getId());
        wishlist.clearItems();

        log.info("Wishlist cleared successfully for customer {}", customerPublicId);
    }

    @Override
    @Transactional(readOnly = true)
    public WishlistCountResponse getWishlistCount(String customerPublicId) {
        validatePublicId(customerPublicId, "Customer public ID");

        int count = wishlistItemRepository.countByCustomerPublicId(customerPublicId);
        return WishlistCountResponse.builder()
                .totalItems(count)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public WishlistStatusResponse isProductWishlisted(String customerPublicId, String productPublicId) {
        validatePublicId(customerPublicId, "Customer public ID");
        validatePublicId(productPublicId, "Product public ID");

        Optional<Wishlist> wishlistOpt = wishlistRepository.findByCustomerPublicId(customerPublicId);
        if (wishlistOpt.isEmpty()) {
            return WishlistStatusResponse.builder()
                    .productPublicId(productPublicId)
                    .isWishlisted(false)
                    .build();
        }

        boolean exists = wishlistItemRepository.existsByWishlistIdAndProductPublicId(wishlistOpt.get().getId(), productPublicId);
        return WishlistStatusResponse.builder()
                .productPublicId(productPublicId)
                .isWishlisted(exists)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public WishlistBulkStatusResponse checkBulkStatus(String customerPublicId, List<String> productPublicIds) {
        validatePublicId(customerPublicId, "Customer public ID");

        if (productPublicIds == null || productPublicIds.isEmpty()) {
            return WishlistBulkStatusResponse.builder()
                    .customerPublicId(customerPublicId)
                    .statusMap(Collections.emptyMap())
                    .items(Collections.emptyList())
                    .build();
        }

        // Validate each UUID
        for (String pid : productPublicIds) {
            validatePublicId(pid, "Product public ID in bulk request");
        }

        List<String> existingProductIds = wishlistItemRepository
                .findExistingProductPublicIdsByCustomerAndProducts(customerPublicId, productPublicIds);

        Set<String> existingSet = new HashSet<>(existingProductIds);
        Map<String, Boolean> statusMap = new HashMap<>();
        List<WishlistStatusResponse> items = new ArrayList<>();

        for (String pid : productPublicIds) {
            boolean isWishlisted = existingSet.contains(pid);
            statusMap.put(pid, isWishlisted);
            items.add(WishlistStatusResponse.builder()
                    .productPublicId(pid)
                    .isWishlisted(isWishlisted)
                    .build());
        }

        return WishlistBulkStatusResponse.builder()
                .customerPublicId(customerPublicId)
                .statusMap(statusMap)
                .items(items)
                .build();
    }

    private WishlistProductItemResponse mapToProductItemResponse(WishlistItem item, ProductDto productDto) {
        if (productDto == null) {
            return WishlistProductItemResponse.builder()
                    .productPublicId(item.getProductPublicId())
                    .productName("Product information unavailable")
                    .availabilityStatus("UNAVAILABLE")
                    .isAvailable(false)
                    .addedAt(item.getAddedAt())
                    .build();
        }

        boolean available = productDto.getAvailable() != null ? productDto.getAvailable() : false;
        String availabilityStatus = available ? "AVAILABLE" :
                (productDto.getStockStatus() != null ? productDto.getStockStatus() : "UNAVAILABLE");

        return WishlistProductItemResponse.builder()
                .productPublicId(item.getProductPublicId())
                .productName(productDto.getName())
                .productSlug(productDto.getSlug())
                .productImage(productDto.getImageUrl())
                .price(productDto.getPricePerKg() != null ? productDto.getPricePerKg() : productDto.getTotalPrice())
                .unit(productDto.getQuantityUnit() != null ? productDto.getQuantityUnit() : "KG")
                .availabilityStatus(availabilityStatus)
                .stockStatus(productDto.getStockStatus())
                .isAvailable(available)
                .addedAt(item.getAddedAt())
                .build();
    }

    private void validatePublicId(String id, String fieldName) {
        if (id == null || !UUID_PATTERN.matcher(id.trim()).matches()) {
            throw new InvalidRequestException(fieldName + " must be a valid 36-character UUID: " + id);
        }
    }
}
