package com.rrvgreenbasket.product.service.integration;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * InventoryIntegrationService
 *
 * <p><strong>Architectural Contract:</strong></p>
 * Clean boundary defining how Product Service discovers real-time stock levels
 * and responds to asynchronous stock level changes from Inventory Service.
 *
 * <ul>
 *   <li>Identifies products by public UUID.</li>
 *   <li>Enforces idempotency on incoming stock events.</li>
 *   <li>Handles service unavailability safely without false-positive availability advertisement.</li>
 *   <li>Validates stock sufficiency against KG quantities.</li>
 * </ul>
 */
public interface InventoryIntegrationService {

    StockAvailabilityResult checkStockAvailability(String productPublicId, BigDecimal requestedQuantityKg);

    Map<String, StockAvailabilityResult> checkBulkStockAvailability(List<String> productPublicIds);

    boolean processStockUpdateEvent(String productPublicId, BigDecimal availableStockKg, String eventId);

    void setSimulatedStock(String productPublicId, BigDecimal availableStockKg);

    void clearSimulatedStock();
}
