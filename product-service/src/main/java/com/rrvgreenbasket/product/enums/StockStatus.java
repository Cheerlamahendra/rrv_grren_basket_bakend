package com.rrvgreenbasket.product.enums;

/**
 * StockStatus
 *
 * <p>Public display stock availability states derived from integration boundary:</p>
 * <ul>
 *   <li>IN_STOCK - Sufficient stock available for purchase.</li>
 *   <li>OUT_OF_STOCK - Depleted inventory or below requested threshold.</li>
 *   <li>UNKNOWN - Inventory service unreachable or unintegrated; fail-safe defaults to unavailable.</li>
 * </ul>
 */
public enum StockStatus {
    IN_STOCK,
    OUT_OF_STOCK,
    UNKNOWN
}
