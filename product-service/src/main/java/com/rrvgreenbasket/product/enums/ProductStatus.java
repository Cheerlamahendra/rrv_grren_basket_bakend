package com.rrvgreenbasket.product.enums;

/**
 * ProductStatus
 *
 * <p><strong>Lifecycle States:</strong></p>
 * <ul>
 *   <li>DRAFT - Product is being created and configured by admin; not visible to customers.</li>
 *   <li>ACTIVE - Product is published and potentially purchasable (subject to admin_enabled and stock).</li>
 *   <li>INACTIVE - Product is temporarily hidden or deactivated.</li>
 *   <li>ARCHIVED - Soft-deleted product retained for audit/order historical integrity.</li>
 * </ul>
 */
public enum ProductStatus {
    DRAFT,
    ACTIVE,
    INACTIVE,
    ARCHIVED
}
