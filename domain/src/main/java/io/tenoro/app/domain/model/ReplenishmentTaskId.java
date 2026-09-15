package io.tenoro.app.domain.model;

import io.tenoro.app.domain.exception.ValidationException;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * UUID identity of a replenishment task. Generated with {@link UUID#randomUUID()}
 * at task creation. Parsing a non-UUID string is a validation error.
 */
public record ReplenishmentTaskId(UUID value) {

    private static final Pattern CANONICAL_UUID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

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
        if (raw == null || !CANONICAL_UUID.matcher(raw).matches()) {
            throw malformed(raw);
        }
        try {
            UUID parsed = UUID.fromString(raw);
            if (!parsed.toString().equalsIgnoreCase(raw)) {
                throw malformed(raw);
            }
            return of(parsed);
        } catch (IllegalArgumentException ex) {
            throw malformed(raw);
        }
    }

    private static ValidationException malformed(String raw) {
        return new ValidationException(
                "id must be a well-formed UUID",
                Map.of("field", "id", "value", raw == null ? "null" : raw));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
