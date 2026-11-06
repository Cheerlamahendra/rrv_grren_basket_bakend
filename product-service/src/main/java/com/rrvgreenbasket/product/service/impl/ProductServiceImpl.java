package com.rrvgreenbasket.product.service.impl;

import com.rrvgreenbasket.product.dto.request.CreateProductRequest;
import com.rrvgreenbasket.product.dto.request.PatchProductRequest;
import com.rrvgreenbasket.product.dto.request.UpdateProductRequest;
import com.rrvgreenbasket.product.dto.response.*;
import com.rrvgreenbasket.product.entity.Product;
import com.rrvgreenbasket.product.entity.ProductCategory;
import com.rrvgreenbasket.product.entity.ProductImage;
import com.rrvgreenbasket.product.entity.ProductS3DeletionOutbox;
import com.rrvgreenbasket.product.enums.ProductStatus;
import com.rrvgreenbasket.product.enums.S3DeletionStatus;
import com.rrvgreenbasket.product.enums.StockStatus;
import com.rrvgreenbasket.product.exception.DuplicateResourceException;
import com.rrvgreenbasket.product.exception.FileUploadException;
import com.rrvgreenbasket.product.exception.InvalidRequestException;
import com.rrvgreenbasket.product.exception.ResourceNotFoundException;
import com.rrvgreenbasket.product.mapper.ProductMapper;
import com.rrvgreenbasket.product.repository.ProductCategoryRepository;
import com.rrvgreenbasket.product.repository.ProductImageRepository;
import com.rrvgreenbasket.product.repository.ProductRepository;
import com.rrvgreenbasket.product.repository.ProductS3DeletionOutboxRepository;
import com.rrvgreenbasket.product.service.ImageStorageService;
import com.rrvgreenbasket.product.service.ProductDeletionGuard;
import com.rrvgreenbasket.product.service.ProductService;
import com.rrvgreenbasket.product.service.integration.InventoryIntegrationService;
import com.rrvgreenbasket.product.service.integration.StockAvailabilityResult;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProductServiceImpl implements ProductService {

    private static final int MAX_PAGE_SIZE = 10;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB
    private static final int MAX_IMAGES_PER_PRODUCT = 5;
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
    );

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductMapper productMapper;
    private final ImageStorageService imageStorageService;
    private final InventoryIntegrationService inventoryIntegrationService;
    private final ProductDeletionGuard productDeletionGuard;
    private final ProductS3DeletionOutboxRepository s3DeletionOutboxRepository;
    private final TransactionTemplate transactionTemplate;

    @org.springframework.beans.factory.annotation.Autowired
    public ProductServiceImpl(
            ProductRepository productRepository,
            ProductCategoryRepository categoryRepository,
            ProductImageRepository productImageRepository,
            ProductMapper productMapper,
            ImageStorageService imageStorageService,
            InventoryIntegrationService inventoryIntegrationService,
            ProductDeletionGuard productDeletionGuard,
            ProductS3DeletionOutboxRepository s3DeletionOutboxRepository,
            PlatformTransactionManager transactionManager) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.productImageRepository = productImageRepository;
        this.productMapper = productMapper;
        this.imageStorageService = imageStorageService;
        this.inventoryIntegrationService = inventoryIntegrationService;
        this.productDeletionGuard = productDeletionGuard;
        this.s3DeletionOutboxRepository = s3DeletionOutboxRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public ProductServiceImpl(
            ProductRepository productRepository,
            ProductCategoryRepository categoryRepository,
            ProductImageRepository productImageRepository,
            ProductMapper productMapper,
            ImageStorageService imageStorageService,
            InventoryIntegrationService inventoryIntegrationService,
            PlatformTransactionManager transactionManager) {
        this(productRepository, categoryRepository, productImageRepository, productMapper,
                imageStorageService, inventoryIntegrationService, null, null, transactionManager);
    }

    // =========================================================================
    // ADMIN PRODUCT OPERATIONS
    // =========================================================================

    @Override
    @Transactional
    public ProductDetailResponse createProduct(CreateProductRequest request, List<MultipartFile> images) {
        // 1. Validate category existence
        ProductCategory category = resolveCategory(request.getEffectiveCategoryId());

        // 2. Validate unique slug
        String slug = (request.getSlug() != null && !request.getSlug().isBlank())
                ? slugify(request.getSlug())
                : generateUniqueSlug(request.getName(), null);

        if (productRepository.existsBySlug(slug)) {
            throw new DuplicateResourceException("Product slug already exists: " + slug);
        }

        // 3. Filter and validate images
        List<MultipartFile> validImages = (images != null)
                ? images.stream().filter(f -> f != null && !f.isEmpty()).toList()
                : Collections.emptyList();

        if (validImages.size() > MAX_IMAGES_PER_PRODUCT) {
            throw new InvalidRequestException("Cannot upload more than " + MAX_IMAGES_PER_PRODUCT
                    + " images per product (supplied: " + validImages.size() + ")");
        }

        for (MultipartFile file : validImages) {
            validateImageFile(file);
        }

        // 4. Pre-generate product public UUID for S3 folder key
        String productPublicId = UUID.randomUUID().toString();

        // 5. Upload all images to AWS S3 with compensation rollback on S3 failure
        List<ImageStorageService.StorageResult> uploadedResults = new ArrayList<>();
        List<String> uploadedKeys = new ArrayList<>();

        for (MultipartFile file : validImages) {
            try {
                ImageStorageService.StorageResult result = imageStorageService.uploadImage(file, "products/" + productPublicId);
                uploadedResults.add(result);
                uploadedKeys.add(result.storageKey());
            } catch (Exception ex) {
                log.error("Failed to upload image {} to S3 for product {}. Rolling back {} uploaded images.",
                        file.getOriginalFilename(), productPublicId, uploadedKeys.size(), ex);
                // Compensation: delete already uploaded S3 objects
                for (String key : uploadedKeys) {
                    try {
                        imageStorageService.deleteImage(key);
                    } catch (Exception cleanupEx) {
                        log.error("CRITICAL: Failed to clean up orphaned S3 object during upload rollback: key={}", key, cleanupEx);
                    }
                }
                if (ex instanceof FileUploadException) {
                    throw (FileUploadException) ex;
                }
                throw new FileUploadException("Image upload to S3 failed: " + ex.getMessage(), ex);
            }
        }

        // 6. Persist to MySQL within transaction. On DB failure, compensate by deleting all uploaded S3 objects!
        Product savedProduct;
        try {
            Product product = Product.builder()
                    .publicId(productPublicId)
                    .category(category)
                    .name(request.getName().trim())
                    .slug(slug)
                    .description(request.getDescription())
                    .shortDescription(request.getShortDescription())
                    .pricePerKg(request.getPricePerKg())
                    .defaultQuantityKg(request.getDefaultQuantityKg() != null ? request.getDefaultQuantityKg() : new BigDecimal("1.000"))
                    .quantityUnit(StringUtils.hasText(request.getQuantityUnit()) ? request.getQuantityUnit() : "KG")
                    .imageUrl(uploadedResults.isEmpty() ? request.getImageUrl() : uploadedResults.get(0).imageUrl())
                    .status(request.getStatus() != null ? request.getStatus() : ProductStatus.DRAFT)
                    .adminEnabled(request.getAdminEnabled() != null ? request.getAdminEnabled() : true)
                    .isFeatured(request.getIsFeatured() != null ? request.getIsFeatured() : false)
                    .images(new ArrayList<>())
                    .build();

            for (int i = 0; i < uploadedResults.size(); i++) {
                ImageStorageService.StorageResult res = uploadedResults.get(i);
                ProductImage img = ProductImage.builder()
                        .publicId(UUID.randomUUID().toString())
                        .product(product)
                        .storageProvider(res.storageProvider())
                        .storageKey(res.storageKey())
                        .imageUrl(res.imageUrl())
                        .altText(product.getName() + " image " + (i + 1))
                        .isPrimary(i == 0) // First uploaded image is primary by default
                        .sortOrder(i)
                        .build();
                product.addImage(img);
            }

            savedProduct = productRepository.save(product);
        } catch (Exception dbEx) {
            log.error("Database transaction failed for product {}. Compensating by cleaning up {} uploaded S3 images.",
                    productPublicId, uploadedKeys.size(), dbEx);
            for (String key : uploadedKeys) {
                try {
                    imageStorageService.deleteImage(key);
                } catch (Exception cleanupEx) {
                    log.error("CRITICAL: Failed to clean up orphaned S3 object after DB transaction failure: key={}", key, cleanupEx);
                }
            }
            throw dbEx;
        }

        log.info("Successfully created product with {} images: publicId={}, name={}",
                uploadedResults.size(), savedProduct.getPublicId(), savedProduct.getName());

        StockAvailabilityResult stock = inventoryIntegrationService.checkStockAvailability(savedProduct.getPublicId(), savedProduct.getDefaultQuantityKg());
        boolean isAvailable = computeFinalAvailability(savedProduct, stock);
        return productMapper.toDetailResponse(savedProduct, isAvailable, stock.getStockStatus());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryResponse> getAdminProducts(
            String categorySlug,
            String search,
            Boolean available,
            ProductStatus status,
            Pageable pageable) {

        int pageSize = pageable.getPageSize() > 0 ? Math.min(pageable.getPageSize(), MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
        Pageable safePageable = PageRequest.of(pageable.getPageNumber(), pageSize, pageable.getSort());

        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(categorySlug)) {
                predicates.add(cb.equal(root.get("category").get("slug"), categorySlug.trim()));
            }

            if (StringUtils.hasText(search)) {
                String searchPattern = "%" + search.trim().toLowerCase() + "%";
                Predicate nameLike = cb.like(cb.lower(root.get("name")), searchPattern);
                Predicate descLike = cb.like(cb.lower(root.get("shortDescription")), searchPattern);
                predicates.add(cb.or(nameLike, descLike));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (available != null) {
                if (available) {
                    predicates.add(cb.equal(root.get("adminEnabled"), true));
                    predicates.add(cb.equal(root.get("status"), ProductStatus.ACTIVE));
                } else {
                    predicates.add(cb.or(
                            cb.equal(root.get("adminEnabled"), false),
                            cb.notEqual(root.get("status"), ProductStatus.ACTIVE)
                    ));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Product> productPage = productRepository.findAll(spec, safePageable);

        List<String> publicIds = productPage.getContent().stream()
                .map(Product::getPublicId)
                .collect(Collectors.toList());

        Map<String, StockAvailabilityResult> stockMap = inventoryIntegrationService.checkBulkStockAvailability(publicIds);

        List<ProductSummaryResponse> summaryList = productPage.getContent().stream()
                .map(p -> {
                    StockAvailabilityResult stock = stockMap.getOrDefault(p.getPublicId(), StockAvailabilityResult.unknown(p.getPublicId()));
                    boolean isAvailable = computeFinalAvailability(p, stock);
                    return productMapper.toSummaryResponse(p, isAvailable, stock.getStockStatus());
                })
                .collect(Collectors.toList());

        return PageResponse.<ProductSummaryResponse>builder()
                .content(summaryList)
                .pageNumber(productPage.getNumber())
                .pageSize(productPage.getSize())
                .totalElements(productPage.getTotalElements())
                .totalPages(productPage.getTotalPages())
                .isLast(productPage.isLast())
                .isFirst(productPage.isFirst())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDetailResponse getAdminProductByPublicId(String publicId) {
        Product product = productRepository.findByPublicIdWithDetails(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with publicId: " + publicId));

        StockAvailabilityResult stock = inventoryIntegrationService.checkStockAvailability(product.getPublicId(), product.getDefaultQuantityKg());
        boolean isAvailable = computeFinalAvailability(product, stock);
        return productMapper.toDetailResponse(product, isAvailable, stock.getStockStatus());
    }

    @Override
    @Transactional
    public ProductDetailResponse updateProduct(String publicId, UpdateProductRequest request, List<MultipartFile> images, Boolean replaceImages) {
        Product product = productRepository.findByPublicIdWithDetails(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with publicId: " + publicId));

        ProductCategory category = resolveCategory(request.getEffectiveCategoryId());

        String slug = (request.getSlug() != null && !request.getSlug().isBlank())
                ? slugify(request.getSlug())
                : slugify(request.getName());

        if (productRepository.existsBySlugAndIdNot(slug, product.getId())) {
            throw new DuplicateResourceException("Product slug already in use by another product: " + slug);
        }

        boolean shouldReplace = Boolean.TRUE.equals(replaceImages) || Boolean.TRUE.equals(request.getReplaceImages());

        List<MultipartFile> validNewImages = (images != null)
                ? images.stream().filter(f -> f != null && !f.isEmpty()).toList()
                : Collections.emptyList();

        int existingImageCount = shouldReplace ? 0 : product.getImages().size();
        if (existingImageCount + validNewImages.size() > MAX_IMAGES_PER_PRODUCT) {
            throw new InvalidRequestException("Cannot exceed " + MAX_IMAGES_PER_PRODUCT
                    + " images per product (existing: " + product.getImages().size() + ", new: " + validNewImages.size() + ")");
        }

        for (MultipartFile file : validNewImages) {
            validateImageFile(file);
        }

        // Upload new images to AWS S3 with compensation rollback on S3 failure
        List<ImageStorageService.StorageResult> newUploadedResults = new ArrayList<>();
        List<String> newUploadedKeys = new ArrayList<>();

        for (MultipartFile file : validNewImages) {
            try {
                ImageStorageService.StorageResult result = imageStorageService.uploadImage(file, "products/" + product.getPublicId());
                newUploadedResults.add(result);
                newUploadedKeys.add(result.storageKey());
            } catch (Exception ex) {
                log.error("Failed to upload new image {} for product {}. Rolling back {} newly uploaded images.",
                        file.getOriginalFilename(), product.getPublicId(), newUploadedKeys.size(), ex);
                for (String key : newUploadedKeys) {
                    try {
                        imageStorageService.deleteImage(key);
                    } catch (Exception cleanupEx) {
                        log.error("CRITICAL: Failed to clean up orphaned S3 object during update upload rollback: key={}", key, cleanupEx);
                    }
                }
                if (ex instanceof FileUploadException) {
                    throw (FileUploadException) ex;
                }
                throw new FileUploadException("Image upload to S3 failed: " + ex.getMessage(), ex);
            }
        }

        // Collect old storage keys to delete only AFTER database transaction succeeds
        List<String> oldKeysToDelete = new ArrayList<>();
        if (shouldReplace && !product.getImages().isEmpty()) {
            for (ProductImage img : product.getImages()) {
                if (StringUtils.hasText(img.getStorageKey())) {
                    oldKeysToDelete.add(img.getStorageKey());
                }
            }
        }

        Product savedProduct;
        try {
            product.setCategory(category);
            product.setName(request.getName().trim());
            product.setSlug(slug);
            product.setDescription(request.getDescription());
            product.setShortDescription(request.getShortDescription());
            product.setPricePerKg(request.getPricePerKg());
            product.setDefaultQuantityKg(request.getDefaultQuantityKg());
            product.setQuantityUnit(request.getQuantityUnit());
            product.setStatus(request.getStatus());
            product.setAdminEnabled(request.getAdminEnabled());
            product.setIsFeatured(request.getIsFeatured());

            if (shouldReplace) {
                product.getImages().clear();
                if (!newUploadedResults.isEmpty()) {
                    for (int i = 0; i < newUploadedResults.size(); i++) {
                        ImageStorageService.StorageResult res = newUploadedResults.get(i);
                        ProductImage img = ProductImage.builder()
                                .publicId(UUID.randomUUID().toString())
                                .product(product)
                                .storageProvider(res.storageProvider())
                                .storageKey(res.storageKey())
                                .imageUrl(res.imageUrl())
                                .altText(product.getName() + " image " + (i + 1))
                                .isPrimary(i == 0)
                                .sortOrder(i)
                                .build();
                        product.addImage(img);
                    }
                    product.setImageUrl(newUploadedResults.get(0).imageUrl());
                } else if (StringUtils.hasText(request.getImageUrl())) {
                    product.setImageUrl(request.getImageUrl());
                } else {
                    product.setImageUrl(null);
                }
            } else if (!newUploadedResults.isEmpty()) {
                // Append new images
                int startingSortOrder = product.getImages().size();
                boolean hasExistingPrimary = product.getImages().stream().anyMatch(img -> Boolean.TRUE.equals(img.getIsPrimary()));

                for (int i = 0; i < newUploadedResults.size(); i++) {
                    ImageStorageService.StorageResult res = newUploadedResults.get(i);
                    boolean isPrimary = !hasExistingPrimary && (i == 0);
                    ProductImage img = ProductImage.builder()
                            .publicId(UUID.randomUUID().toString())
                            .product(product)
                            .storageProvider(res.storageProvider())
                            .storageKey(res.storageKey())
                            .imageUrl(res.imageUrl())
                            .altText(product.getName() + " image " + (startingSortOrder + i + 1))
                            .isPrimary(isPrimary)
                            .sortOrder(startingSortOrder + i)
                            .build();
                    product.addImage(img);
                    if (isPrimary) {
                        product.setImageUrl(res.imageUrl());
                    }
                }
            } else if (StringUtils.hasText(request.getImageUrl())) {
                product.setImageUrl(request.getImageUrl());
            }

            savedProduct = productRepository.save(product);
        } catch (Exception dbEx) {
            log.error("Database transaction failed for product update {}. Compensating by cleaning up {} newly uploaded S3 images.",
                    product.getPublicId(), newUploadedKeys.size(), dbEx);
            for (String key : newUploadedKeys) {
                try {
                    imageStorageService.deleteImage(key);
                } catch (Exception cleanupEx) {
                    log.error("CRITICAL: Failed to clean up orphaned S3 object after DB update failure: key={}", key, cleanupEx);
                }
            }
            throw dbEx;
        }

        // Clean up replaced images from S3 strictly AFTER successful DB commit
        if (!oldKeysToDelete.isEmpty()) {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        for (String oldKey : oldKeysToDelete) {
                            try {
                                imageStorageService.deleteImage(oldKey);
                                log.info("Deleted replaced old image from S3: key={}", oldKey);
                            } catch (Exception ex) {
                                log.warn("Failed to delete old image from S3 during replacement: key={}", oldKey, ex);
                            }
                        }
                    }
                });
            } else {
                for (String oldKey : oldKeysToDelete) {
                    try {
                        imageStorageService.deleteImage(oldKey);
                        log.info("Deleted replaced old image from S3: key={}", oldKey);
                    } catch (Exception ex) {
                        log.warn("Failed to delete old image from S3 during replacement: key={}", oldKey, ex);
                    }
                }
            }
        }

        log.info("Updated product: publicId={}, name={}", savedProduct.getPublicId(), savedProduct.getName());

        StockAvailabilityResult stock = inventoryIntegrationService.checkStockAvailability(savedProduct.getPublicId(), savedProduct.getDefaultQuantityKg());
        boolean isAvailable = computeFinalAvailability(savedProduct, stock);
        return productMapper.toDetailResponse(savedProduct, isAvailable, stock.getStockStatus());
    }

    @Override
    @Transactional
    public ProductDetailResponse patchProduct(String publicId, PatchProductRequest request) {
        Product product = productRepository.findByPublicIdWithDetails(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with publicId: " + publicId));

        if (StringUtils.hasText(request.getCategoryId())) {
            product.setCategory(resolveCategory(request.getCategoryId()));
        }

        if (StringUtils.hasText(request.getName())) {
            product.setName(request.getName().trim());
        }

        if (StringUtils.hasText(request.getSlug())) {
            String slug = slugify(request.getSlug());
            if (productRepository.existsBySlugAndIdNot(slug, product.getId())) {
                throw new DuplicateResourceException("Product slug already in use: " + slug);
            }
            product.setSlug(slug);
        }

        if (request.getDescription() != null) {
            product.setDescription(request.getDescription());
        }

        if (request.getShortDescription() != null) {
            product.setShortDescription(request.getShortDescription());
        }

        if (request.getPricePerKg() != null) {
            product.setPricePerKg(request.getPricePerKg());
        }

        if (request.getDefaultQuantityKg() != null) {
            product.setDefaultQuantityKg(request.getDefaultQuantityKg());
        }

        if (StringUtils.hasText(request.getQuantityUnit())) {
            product.setQuantityUnit(request.getQuantityUnit());
        }

        if (StringUtils.hasText(request.getImageUrl())) {
            product.setImageUrl(request.getImageUrl());
        }

        if (request.getStatus() != null) {
            product.setStatus(request.getStatus());
        }

        if (request.getAdminEnabled() != null) {
            product.setAdminEnabled(request.getAdminEnabled());
        }

        if (request.getIsFeatured() != null) {
            product.setIsFeatured(request.getIsFeatured());
        }

        Product updated = productRepository.save(product);
        log.info("Patched product: publicId={}, name={}", updated.getPublicId(), updated.getName());

        StockAvailabilityResult stock = inventoryIntegrationService.checkStockAvailability(updated.getPublicId(), updated.getDefaultQuantityKg());
        boolean isAvailable = computeFinalAvailability(updated, stock);
        return productMapper.toDetailResponse(updated, isAvailable, stock.getStockStatus());
    }

    @Override
    @Transactional
    public void deleteProduct(String publicId) {
        Product product = productRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with publicId: " + publicId));

        // Soft delete / archive to protect historical orders and audit trails
        product.setStatus(ProductStatus.ARCHIVED);
        product.setAdminEnabled(false);
        productRepository.save(product);
        log.info("Soft-deleted (archived) product: publicId={}, name={}", publicId, product.getName());
    }

    @Override
    @Transactional
    public void permanentlyDeleteProduct(String publicId) {
        log.info("Initiating permanent deletion for product: publicId={}", publicId);

        Product product = productRepository.findByPublicIdWithDetails(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with publicId: " + publicId));

        // 1. Guard against permanent deletion if external dependencies (Order Service, Inventory Service) exist
        if (productDeletionGuard != null) {
            productDeletionGuard.validateProductCanBeDeleted(publicId);
        }

        // 2. Collect image storage keys for reliable asynchronous S3 deletion via transactional outbox
        List<ProductImage> images = product.getImages();
        if (images != null && !images.isEmpty()) {
            for (ProductImage image : images) {
                if (StringUtils.hasText(image.getStorageKey())) {
                    ProductS3DeletionOutbox outboxEntry = ProductS3DeletionOutbox.builder()
                            .publicId(UUID.randomUUID().toString())
                            .productPublicId(publicId)
                            .storageKey(image.getStorageKey())
                            .status(S3DeletionStatus.PENDING)
                            .retryCount(0)
                            .maxRetries(5)
                            .nextRetryAt(Instant.now())
                            .build();
                    if (s3DeletionOutboxRepository != null) {
                        s3DeletionOutboxRepository.save(outboxEntry);
                    }
                    log.info("Enqueued S3 deletion outbox entry for image key '{}' of product '{}'",
                            image.getStorageKey(), publicId);
                }
            }
        }

        // 3. Delete product from database (cascades to product_images via JPA and MySQL foreign key)
        productRepository.delete(product);

        log.info("Successfully deleted product {} and associated images from database; S3 cleanup queued", publicId);
    }

    @Override
    @Transactional
    public ProductDetailResponse updateAvailability(String publicId, boolean adminEnabled) {
        Product product = productRepository.findByPublicIdWithDetails(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with publicId: " + publicId));

        product.setAdminEnabled(adminEnabled);
        Product saved = productRepository.save(product);
        log.info("Updated product admin_enabled: publicId={}, adminEnabled={}", publicId, adminEnabled);

        StockAvailabilityResult stock = inventoryIntegrationService.checkStockAvailability(saved.getPublicId(), saved.getDefaultQuantityKg());
        boolean isAvailable = computeFinalAvailability(saved, stock);
        return productMapper.toDetailResponse(saved, isAvailable, stock.getStockStatus());
    }

    @Override
    @Transactional
    public ProductDetailResponse updateStatus(String publicId, ProductStatus status) {
        Product product = productRepository.findByPublicIdWithDetails(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with publicId: " + publicId));

        product.setStatus(status);
        Product saved = productRepository.save(product);
        log.info("Updated product status: publicId={}, status={}", publicId, status);

        StockAvailabilityResult stock = inventoryIntegrationService.checkStockAvailability(saved.getPublicId(), saved.getDefaultQuantityKg());
        boolean isAvailable = computeFinalAvailability(saved, stock);
        return productMapper.toDetailResponse(saved, isAvailable, stock.getStockStatus());
    }

    // =========================================================================
    // PRODUCT IMAGE MANAGEMENT (S3 + DB)
    // =========================================================================

    @Override
    @Transactional
    public void deleteProductImage(String productPublicId, String imagePublicId) {
        Product product = productRepository.findByPublicId(productPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with publicId: " + productPublicId));

        ProductImage image = productImageRepository.findByPublicId(imagePublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Product image not found with publicId: " + imagePublicId));

        if (!image.getProduct().getId().equals(product.getId())) {
            throw new InvalidRequestException("Image does not belong to the specified product");
        }

        boolean wasPrimary = Boolean.TRUE.equals(image.getIsPrimary());
        String storageKey = image.getStorageKey();

        // Remove from database
        product.getImages().remove(image);
        productImageRepository.delete(image);

        // Delete from S3 storage
        imageStorageService.deleteImage(storageKey);

        // If removed image was primary, select another image if available
        if (wasPrimary) {
            Optional<ProductImage> nextPrimary = product.getImages().stream().findFirst();
            if (nextPrimary.isPresent()) {
                ProductImage newPrimary = nextPrimary.get();
                newPrimary.setIsPrimary(true);
                productImageRepository.save(newPrimary);
                product.setImageUrl(newPrimary.getImageUrl());
            } else {
                product.setImageUrl(null);
            }
            productRepository.save(product);
        }

        log.info("Deleted product image: publicId={}, storageKey={}", imagePublicId, storageKey);
    }

    @Override
    @Transactional
    public ProductDetailResponse setPrimaryImage(String productPublicId, String imagePublicId) {
        Product product = productRepository.findByPublicIdWithDetails(productPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with publicId: " + productPublicId));

        ProductImage targetImage = productImageRepository.findByPublicId(imagePublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Product image not found with publicId: " + imagePublicId));

        if (!targetImage.getProduct().getId().equals(product.getId())) {
            throw new InvalidRequestException("Image does not belong to the specified product");
        }

        for (ProductImage img : product.getImages()) {
            img.setIsPrimary(img.getId().equals(targetImage.getId()));
            productImageRepository.save(img);
        }

        product.setImageUrl(targetImage.getImageUrl());
        Product updated = productRepository.save(product);

        StockAvailabilityResult stock = inventoryIntegrationService.checkStockAvailability(updated.getPublicId(), updated.getDefaultQuantityKg());
        boolean isAvailable = computeFinalAvailability(updated, stock);
        return productMapper.toDetailResponse(updated, isAvailable, stock.getStockStatus());
    }

    // =========================================================================
    // CUSTOMER CATALOG OPERATIONS (Public, No Pagination, Strictly Available)
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<ProductSummaryResponse> getCustomerCatalog() {
        List<Product> products = productRepository.findAllAvailableForCatalog();
        return filterAndMapAvailableProducts(products);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDetailResponse getCustomerProductByPublicId(String publicId) {
        Product product = productRepository.findAvailableByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Product is unavailable or does not exist: " + publicId));

        StockAvailabilityResult stock = inventoryIntegrationService.checkStockAvailability(product.getPublicId(), product.getDefaultQuantityKg());
        boolean isAvailable = computeFinalAvailability(product, stock);

        if (!isAvailable) {
            throw new ResourceNotFoundException("Product is currently unavailable for purchase: " + publicId);
        }

        return productMapper.toDetailResponse(product, true, stock.getStockStatus());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductSummaryResponse> getCustomerProductsByCategorySlug(String categorySlug) {
        if (!StringUtils.hasText(categorySlug)) {
            return Collections.emptyList();
        }
        List<Product> products = productRepository.findAvailableByCategorySlug(categorySlug.trim());
        return filterAndMapAvailableProducts(products);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductSummaryResponse> getCustomerFeaturedProducts() {
        List<Product> products = productRepository.findAvailableFeatured();
        return filterAndMapAvailableProducts(products);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductAvailabilityResponse checkProductAvailability(String publicId, BigDecimal requestedQuantityKg) {
        Product product = productRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with publicId: " + publicId));

        BigDecimal requestedQty = (requestedQuantityKg != null && requestedQuantityKg.compareTo(BigDecimal.ZERO) > 0)
                ? requestedQuantityKg
                : product.getDefaultQuantityKg();

        StockAvailabilityResult stock = inventoryIntegrationService.checkStockAvailability(publicId, requestedQty);
        boolean isAvailable = computeFinalAvailability(product, stock);

        BigDecimal totalPrice = product.getPricePerKg()
                .multiply(requestedQty)
                .setScale(2, RoundingMode.HALF_UP);

        String reason = isAvailable ? "Product is available for purchase"
                : (!Boolean.TRUE.equals(product.getAdminEnabled()) ? "Product is disabled by store administration"
                : (product.getStatus() != ProductStatus.ACTIVE ? "Product is currently inactive"
                : stock.getReason()));

        return ProductAvailabilityResponse.builder()
                .publicId(product.getPublicId())
                .name(product.getName())
                .available(isAvailable)
                .adminEnabled(product.getAdminEnabled())
                .productStatus(product.getStatus().name())
                .stockStatus(stock.getStockStatus())
                .requestedQuantityKg(requestedQty)
                .pricePerKg(product.getPricePerKg())
                .totalPrice(totalPrice)
                .reason(reason)
                .build();
    }

    // =========================================================================
    // HELPER & VALIDATION METHODS
    // =========================================================================

    private boolean computeFinalAvailability(Product product, StockAvailabilityResult stock) {
        return product.getStatus() == ProductStatus.ACTIVE
                && Boolean.TRUE.equals(product.getAdminEnabled())
                && stock.isAvailable();
    }

    private List<ProductSummaryResponse> filterAndMapAvailableProducts(List<Product> products) {
        if (products.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> publicIds = products.stream().map(Product::getPublicId).collect(Collectors.toList());
        Map<String, StockAvailabilityResult> stockMap = inventoryIntegrationService.checkBulkStockAvailability(publicIds);

        return products.stream()
                .filter(p -> {
                    StockAvailabilityResult stock = stockMap.getOrDefault(p.getPublicId(), StockAvailabilityResult.unknown(p.getPublicId()));
                    return computeFinalAvailability(p, stock);
                })
                .map(p -> {
                    StockAvailabilityResult stock = stockMap.get(p.getPublicId());
                    return productMapper.toSummaryResponse(p, true, stock.getStockStatus());
                })
                .collect(Collectors.toList());
    }

    private ProductCategory resolveCategory(String identifier) {
        if (!StringUtils.hasText(identifier)) {
            throw new InvalidRequestException("Category identifier must not be empty");
        }

        return categoryRepository.findByPublicId(identifier.trim())
                .or(() -> categoryRepository.findBySlug(identifier.trim()))
                .orElseThrow(() -> new ResourceNotFoundException("Product category not found: " + identifier));
    }

    private String slugify(String input) {
        if (input == null) {
            return "";
        }
        return input.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");
    }

    private String generateUniqueSlug(String name, Long currentProductId) {
        String baseSlug = slugify(name);
        String candidateSlug = baseSlug;
        int counter = 1;

        while (true) {
            boolean exists = (currentProductId == null)
                    ? productRepository.existsBySlug(candidateSlug)
                    : productRepository.existsBySlugAndIdNot(candidateSlug, currentProductId);

            if (!exists) {
                return candidateSlug;
            }
            candidateSlug = baseSlug + "-" + counter++;
        }
    }

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileUploadException("Image file cannot be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new FileUploadException("Image size exceeds maximum allowed limit of 5 MB (actual: " + file.getSize() + " bytes)");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw new FileUploadException("Unsupported image format: " + contentType + ". Only JPEG, PNG, and WebP are allowed.");
        }
    }
}
