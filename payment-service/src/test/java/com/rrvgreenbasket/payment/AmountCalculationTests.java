package com.rrvgreenbasket.payment;

import com.rrvgreenbasket.payment.config.PartialCodProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Unit Tests — 50% Advance & Partial COD Amount Calculations")
class AmountCalculationTests {

    private final PartialCodProperties properties = new PartialCodProperties();

    @Test
    @DisplayName("Verify default configuration: 50% advance + 50% delivery cash")
    void testDefaultConfiguration() {
        assertTrue(properties.isEnabled());
        assertEquals(50, properties.getAdvancePercentage());
        assertEquals(50, properties.getDeliveryCollectionPercentage());
        assertEquals(100, properties.getAdvancePercentage() + properties.getDeliveryCollectionPercentage());
        assertEquals("INR", properties.getCurrency());
    }

    @Test
    @DisplayName("Reject invalid configuration where percentages do not total 100%")
    void testInvalidConfigurationValidation() {
        PartialCodProperties invalid = new PartialCodProperties();
        invalid.setAdvancePercentage(40);
        invalid.setDeliveryCollectionPercentage(50);
        assertThrows(IllegalStateException.class, invalid::validatePercentages);
    }

    @Test
    @DisplayName("Reject out-of-range advance percentage")
    void testOutOfRangeAdvancePercentage() {
        PartialCodProperties invalid = new PartialCodProperties();
        invalid.setAdvancePercentage(0);
        invalid.setDeliveryCollectionPercentage(100);
        assertThrows(IllegalStateException.class, invalid::validatePercentages);

        PartialCodProperties invalidHigh = new PartialCodProperties();
        invalidHigh.setAdvancePercentage(100);
        invalidHigh.setDeliveryCollectionPercentage(0);
        assertThrows(IllegalStateException.class, invalidHigh::validatePercentages);
    }

    @ParameterizedTest(name = "Order ₹{0} -> Advance ₹{1}, Delivery ₹{2}, Advance Paise {3}")
    @CsvSource({
            "400.00, 200.00, 200.00, 20000",
            "100.00, 50.00, 50.00, 5000",
            "250.00, 125.00, 125.00, 12500",
            "999.00, 499.50, 499.50, 49950",
            "1500.00, 750.00, 750.00, 75000",
            "2499.00, 1249.50, 1249.50, 124950"
    })
    @DisplayName("Parametric test for exact INR rounding, advance calculations, and paise conversion")
    void testAdvanceAndDeliveryAmountCalculations(
            String totalStr, String expectedAdvanceStr, String expectedDeliveryStr, long expectedPaise) {

        BigDecimal total = new BigDecimal(totalStr);
        int advancePercentage = properties.getAdvancePercentage();

        BigDecimal advanceAmount = total
                .multiply(BigDecimal.valueOf(advancePercentage))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal deliveryAmount = total.subtract(advanceAmount);

        assertEquals(new BigDecimal(expectedAdvanceStr), advanceAmount);
        assertEquals(new BigDecimal(expectedDeliveryStr), deliveryAmount);

        // Invariant: advanceAmount + deliveryAmount must ALWAYS equal totalAmount
        assertEquals(total, advanceAmount.add(deliveryAmount));

        // Invariant: Razorpay amount in paise
        long advancePaise = advanceAmount.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();

        assertEquals(expectedPaise, advancePaise);
    }
}
