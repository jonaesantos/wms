package io.tenoro.app.domain.model;

import io.tenoro.app.domain.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class LocationTypeTest {

    @Test
    void parsesSupportedTypes() {
        assertEquals(LocationType.PICKING, LocationType.from("PICKING"));
        assertEquals(LocationType.RESERVE, LocationType.from("RESERVE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"picking", "Reserve", "WAREHOUSE", "  ", "PICK"})
    void rejectsUnsupportedOrBlankValues(String value) {
        assertThrows(ValidationException.class, () -> LocationType.from(value));
    }

    @Test
    void rejectsNull() {
        assertThrows(ValidationException.class, () -> LocationType.from(null));
    }
}
