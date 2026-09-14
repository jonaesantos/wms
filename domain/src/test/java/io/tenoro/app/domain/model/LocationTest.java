package io.tenoro.app.domain.model;

import io.tenoro.app.domain.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LocationTest {

    @Test
    void buildsFromRawValues() {
        Location location = Location.of("PICK-01", "PICKING");
        assertEquals(LocationCode.of("PICK-01"), location.code());
        assertEquals(LocationType.PICKING, location.type());
    }

    @Test
    void rejectsInvalidCode() {
        assertThrows(ValidationException.class, () -> Location.of(" PICK-01", "PICKING"));
    }

    @Test
    void rejectsInvalidType() {
        assertThrows(ValidationException.class, () -> Location.of("PICK-01", "INVALID"));
    }
}
