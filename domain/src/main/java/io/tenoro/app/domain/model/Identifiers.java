package io.tenoro.app.domain.model;

import io.tenoro.app.domain.exception.ValidationException;

import java.util.Map;

/**
 * Shared strict-identifier validation for inventory value objects.
 *
 * <p>Identifiers are case-sensitive and are never silently normalized: a null,
 * empty, blank, or surrounding-whitespace value is rejected rather than trimmed.
 */
final class Identifiers {

    private Identifiers() {
    }

    static String requireStrict(String field, String value) {
        if (value == null) {
            throw new ValidationException(
                    field + " must not be null",
                    Map.of("field", field, "reason", "null"));
        }
        if (value.isBlank()) {
            throw new ValidationException(
                    field + " must not be blank",
                    Map.of("field", field, "reason", "blank"));
        }
        if (!value.equals(value.strip())) {
            throw new ValidationException(
                    field + " must not have surrounding whitespace",
                    Map.of("field", field, "value", value, "reason", "surrounding-whitespace"));
        }
        return value;
    }
}
