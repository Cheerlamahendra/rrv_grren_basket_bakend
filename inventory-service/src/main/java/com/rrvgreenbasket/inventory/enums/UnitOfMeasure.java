package com.rrvgreenbasket.inventory.enums;

public enum UnitOfMeasure {
    KG,
    GRAM,
    PIECE,
    BUNCH,
    PACK,
    PACKET, // Backward-compatible alias for PACK
    DOZEN,
    LITRE,
    ML;

    public static UnitOfMeasure fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Measurement unit cannot be null or blank");
        }
        String normalized = value.trim().toUpperCase();
        if ("PACKET".equals(normalized)) {
            return PACK; // Canonical normalization to PACK
        }
        return UnitOfMeasure.valueOf(normalized);
    }

    public boolean matches(String otherUnit) {
        if (otherUnit == null || otherUnit.isBlank()) {
            return false;
        }
        try {
            UnitOfMeasure parsed = fromString(otherUnit);
            UnitOfMeasure thisCanonical = (this == PACKET) ? PACK : this;
            UnitOfMeasure otherCanonical = (parsed == PACKET) ? PACK : parsed;
            return thisCanonical == otherCanonical;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
