package io.tenoro.app.domain.model;

import io.tenoro.app.domain.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class SkuTest {

    @Test
    void acceptsValidValueWithoutNormalizing() {
        Sku sku = Sku.of("SKU-100");
        assertEquals("SKU-100", sku.value());
    }

    @Test
    void isCaseSensitive() {
        assertNotEquals(Sku.of("SKU-100"), Sku.of("sku-100"));
    }

    @Test
    void rejectsNull() {
        assertThrows(ValidationException.class, () -> Sku.of(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t"})
    void rejectsEmptyOrBlank(String value) {
        assertThrows(ValidationException.class, () -> Sku.of(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {" SKU-100", "SKU-100 ", " SKU-100 "})
    void rejectsSurroundingWhitespace(String value) {
        assertThrows(ValidationException.class, () -> Sku.of(value));
    }
}
