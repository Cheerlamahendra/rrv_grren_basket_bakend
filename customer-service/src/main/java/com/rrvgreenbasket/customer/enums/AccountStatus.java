package com.rrvgreenbasket.customer.enums;

/**
 * AccountStatus
 *
 * <p><strong>What it does:</strong></p>
 * Represents the account standing of a customer within RRV Green Basket.
 * Enforced at database level via CHECK constraint:
 * <code>chk_customers_status CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED'))</code>
 */
public enum AccountStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED
}
