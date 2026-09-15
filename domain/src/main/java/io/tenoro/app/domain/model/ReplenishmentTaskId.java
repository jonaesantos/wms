package io.tenoro.app.domain.model;

import io.tenoro.app.domain.exception.ValidationException;

import java.util.Map;
import java.util.UUID;

/**
 * UUID identity of a replenishment task. Generated with {@link UUID#randomUUID()}
 * at task creation. Parsing a non-UUID string is a validation error.
 */
public record ReplenishmentTaskId(UUID value) {

    public ReplenishmentTaskId {
        if (value == null) {
            throw new ValidationException("id must not be null", Map.of("field", "id", "reason", "null"));
        }
    }

    public static ReplenishmentTaskId generate() {
        return new ReplenishmentTaskId(UUID.randomUUID());
    }

    public static ReplenishmentTaskId of(UUID value) {
        return new ReplenishmentTaskId(value);
    }

    public static ReplenishmentTaskId parse(String raw) {
        if (raw == null || raw.isBlank() || !raw.equals(raw.strip())) {
            throw new ValidationException(
                    "id must be a well-formed UUID",
                    Map.of("field", "id", "value", raw == null ? "null" : raw));
        }
        try {
            return of(UUID.fromString(raw));
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(
                    "id must be a well-formed UUID",
                    Map.of("field", "id", "value", raw));
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
