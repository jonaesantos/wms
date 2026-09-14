package io.tenoro.app.domain.model;

import io.tenoro.app.domain.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class LocationCodeTest {

    @Test
    void acceptsValidValueWithoutNormalizing() {
        LocationCode code = LocationCode.of("PICK-01");
        assertEquals("PICK-01", code.value());
    }

    @Test
    void isCaseSensitive() {
        assertNotEquals(LocationCode.of("PICK-01"), LocationCode.of("pick-01"));
    }

    @Test
    void rejectsNull() {
        assertThrows(ValidationException.class, () -> LocationCode.of(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rejectsEmptyOrBlank(String value) {
        assertThrows(ValidationException.class, () -> LocationCode.of(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {" PICK-01", "PICK-01 ", " PICK-01 "})
    void rejectsSurroundingWhitespace(String value) {
        assertThrows(ValidationException.class, () -> LocationCode.of(value));
    }
}
