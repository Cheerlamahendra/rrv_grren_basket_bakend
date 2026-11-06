package com.rrvgreenbasket.inventory;

import com.rrvgreenbasket.inventory.enums.UnitOfMeasure;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnitOfMeasureTests {

    @ParameterizedTest
    @ValueSource(strings = {"KG", "GRAM", "PIECE", "BUNCH", "PACK", "DOZEN", "LITRE", "ML"})
    @DisplayName("Verify all standard measurement units parse successfully")
    void verifyStandardUnitsParse(String unitStr) {
        UnitOfMeasure unit = UnitOfMeasure.fromString(unitStr);
        assertThat(unit.name()).isEqualTo(unitStr);
    }

    @Test
    @DisplayName("Verify PACKET parses and normalizes canonically to PACK")
    void verifyPacketNormalizesToPack() {
        UnitOfMeasure unit = UnitOfMeasure.fromString("PACKET");
        assertThat(unit).isEqualTo(UnitOfMeasure.PACK);

        UnitOfMeasure lowercase = UnitOfMeasure.fromString("packet");
        assertThat(lowercase).isEqualTo(UnitOfMeasure.PACK);
    }

    @Test
    @DisplayName("Verify case-insensitivity and whitespace trimming")
    void verifyCaseAndWhitespace() {
        assertThat(UnitOfMeasure.fromString("  kg  ")).isEqualTo(UnitOfMeasure.KG);
        assertThat(UnitOfMeasure.fromString("dozen")).isEqualTo(UnitOfMeasure.DOZEN);
        assertThat(UnitOfMeasure.fromString("Piece")).isEqualTo(UnitOfMeasure.PIECE);
    }

    @Test
    @DisplayName("Verify matches() handles PACK and PACKET interchangeably")
    void verifyMatchesPackAndPacket() {
        UnitOfMeasure pack = UnitOfMeasure.PACK;
        assertThat(pack.matches("PACK")).isTrue();
        assertThat(pack.matches("PACKET")).isTrue();
        assertThat(pack.matches("packet")).isTrue();
        assertThat(pack.matches("KG")).isFalse();
    }

    @Test
    @DisplayName("Verify invalid unit strings throw IllegalArgumentException")
    void verifyInvalidUnitThrows() {
        assertThatThrownBy(() -> UnitOfMeasure.fromString("INVALID_UNIT"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> UnitOfMeasure.fromString(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or blank");

        assertThatThrownBy(() -> UnitOfMeasure.fromString(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or blank");
    }
}
