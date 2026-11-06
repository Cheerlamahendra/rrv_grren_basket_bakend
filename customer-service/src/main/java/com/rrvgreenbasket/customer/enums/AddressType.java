package com.rrvgreenbasket.customer.enums;

/**
 * AddressType
 *
 * <p><strong>What it does:</strong></p>
 * Categorizes delivery destinations (e.g., residential, workplace, or other).
 * Enforced at database level via CHECK constraint:
 * <code>chk_customer_address_type CHECK (address_type IN ('HOME','WORK','OTHER'))</code>
 */
public enum AddressType {
    HOME,
    WORK,
    OTHER
}
