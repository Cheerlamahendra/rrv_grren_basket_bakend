package com.rrvgreenbasket.product.service.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InventoryIntegrationServiceImpl implements InventoryIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(InventoryIntegrationServiceImpl.class);

    private final Map<String, BigDecimal> stockCache = new ConcurrentHashMap<>();
    private final Set<String> processedEventIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Value("${app.inventory.mock-default-available:true}")
    private boolean mockDefaultAvailable;

    @Value("${app.inventory.mock-default-quantity-kg:100.000}")
    private BigDecimal mockDefaultQuantityKg;

    @Override
    public StockAvailabilityResult checkStockAvailability(String productPublicId, BigDecimal requestedQuantityKg) {
        if (productPublicId == null || productPublicId.isBlank()) {
            return StockAvailabilityResult.unknown(productPublicId);
        }

        BigDecimal requested = (requestedQuantityKg != null && requestedQuantityKg.compareTo(BigDecimal.ZERO) > 0)
                ? requestedQuantityKg
                : new BigDecimal("1.000");

        BigDecimal availableStock = stockCache.get(productPublicId);

        if (availableStock == null) {
            if (mockDefaultAvailable) {
                availableStock = mockDefaultQuantityKg;
            } else {
                log.warn("Stock level unknown for product '{}'. Failing safe to unavailable.", productPublicId);
                return StockAvailabilityResult.unknown(productPublicId);
            }
        }

        if (availableStock.compareTo(requested) >= 0) {
            return StockAvailabilityResult.available(productPublicId, availableStock);
        } else {
            return StockAvailabilityResult.insufficient(productPublicId, availableStock, requested);
        }
    }

    @Override
    public Map<String, StockAvailabilityResult> checkBulkStockAvailability(List<String> productPublicIds) {
        Map<String, StockAvailabilityResult> results = new HashMap<>();
        if (productPublicIds == null || productPublicIds.isEmpty()) {
            return results;
        }

        for (String publicId : productPublicIds) {
            results.put(publicId, checkStockAvailability(publicId, new BigDecimal("1.000")));
        }
        return results;
    }

    @Override
    public boolean processStockUpdateEvent(String productPublicId, BigDecimal availableStockKg, String eventId) {
        if (eventId != null && !eventId.isBlank()) {
            if (!processedEventIds.add(eventId)) {
                log.info("Duplicate inventory stock event '{}' ignored for product '{}'", eventId, productPublicId);
                return false;
            }
        }

        BigDecimal stock = (availableStockKg != null && availableStockKg.compareTo(BigDecimal.ZERO) >= 0)
                ? availableStockKg
                : BigDecimal.ZERO;

        stockCache.put(productPublicId, stock);
        log.info("Updated inventory stock cache for product '{}': {} KG (eventId={})", productPublicId, stock, eventId);
        return true;
    }

    @Override
    public void setSimulatedStock(String productPublicId, BigDecimal availableStockKg) {
        if (productPublicId != null) {
            stockCache.put(productPublicId, availableStockKg);
        }
    }

    @Override
    public void clearSimulatedStock() {
        stockCache.clear();
        processedEventIds.clear();
    }
}
