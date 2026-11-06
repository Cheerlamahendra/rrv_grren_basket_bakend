package com.rrvgreenbasket.inventory;

import com.rrvgreenbasket.inventory.entity.InventoryItem;
import com.rrvgreenbasket.inventory.enums.StockStatus;
import com.rrvgreenbasket.inventory.enums.UnitOfMeasure;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryItemEntityTests {

    @Test
    @DisplayName("Verify recalculateAvailable computes available = physical - reserved")
    void testRecalculateAvailable() {
        InventoryItem item = InventoryItem.builder()
                .productPublicId(UUID.randomUUID().toString())
                .unitOfMeasure(UnitOfMeasure.KG)
                .physicalQuantity(new BigDecimal("100.500"))
                .reservedQuantity(new BigDecimal("25.250"))
                .lowStockThreshold(new BigDecimal("10.000"))
                .build();

        item.recalculateAvailable();

        assertThat(item.getAvailableQuantity()).isEqualByComparingTo(new BigDecimal("75.250"));
    }

    @Test
    @DisplayName("Verify recalculateAvailable throws when reserved exceeds physical")
    void testRecalculateAvailableNegativeThrows() {
        InventoryItem item = InventoryItem.builder()
                .productPublicId(UUID.randomUUID().toString())
                .unitOfMeasure(UnitOfMeasure.KG)
                .physicalQuantity(new BigDecimal("10.000"))
                .reservedQuantity(new BigDecimal("15.000"))
                .build();

        assertThatThrownBy(item::recalculateAvailable)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Available quantity cannot be negative");
    }

    @Test
    @DisplayName("Verify stock status calculation: OUT_OF_STOCK, LOW_STOCK, IN_STOCK")
    void testStockStatusTransitions() {
        InventoryItem item = InventoryItem.builder()
                .productPublicId(UUID.randomUUID().toString())
                .unitOfMeasure(UnitOfMeasure.PIECE)
                .physicalQuantity(BigDecimal.ZERO.setScale(3))
                .reservedQuantity(BigDecimal.ZERO.setScale(3))
                .lowStockThreshold(new BigDecimal("10.000"))
                .build();
        item.recalculateAvailable();
        assertThat(item.calculateStockStatus()).isEqualTo(StockStatus.OUT_OF_STOCK);

        // Low stock (available <= threshold)
        item.setPhysicalQuantity(new BigDecimal("8.000"));
        item.recalculateAvailable();
        assertThat(item.calculateStockStatus()).isEqualTo(StockStatus.LOW_STOCK);

        // Exact threshold is still LOW_STOCK
        item.setPhysicalQuantity(new BigDecimal("10.000"));
        item.recalculateAvailable();
        assertThat(item.calculateStockStatus()).isEqualTo(StockStatus.LOW_STOCK);

        // In stock (available > threshold)
        item.setPhysicalQuantity(new BigDecimal("10.001"));
        item.recalculateAvailable();
        assertThat(item.calculateStockStatus()).isEqualTo(StockStatus.IN_STOCK);
    }

    @Test
    @DisplayName("Verify ensureInvariantsOnPersist sets defaults and generates UUID")
    void testPrePersistInvariants() {
        InventoryItem item = new InventoryItem();
        item.setProductPublicId(UUID.randomUUID().toString());
        item.setUnitOfMeasure(UnitOfMeasure.DOZEN);

        item.ensureInvariantsOnPersist();

        assertThat(item.getPublicId()).isNotBlank();
        assertThat(item.getPhysicalQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(item.getReservedQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(item.getAvailableQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(item.getLowStockThreshold()).isEqualByComparingTo(new BigDecimal("10.000"));
        assertThat(item.getInitialized()).isFalse();
    }
}
