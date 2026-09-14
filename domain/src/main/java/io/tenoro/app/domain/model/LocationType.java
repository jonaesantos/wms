package io.tenoro.app.domain.model;

import io.tenoro.app.domain.exception.ValidationException;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The role a warehouse location plays. Parsing is strict and case-sensitive so
 * unsupported values surface as a domain validation error rather than a 500.
 */
public enum LocationType {
    PICKING,
    RESERVE;

    public static LocationType from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException(
                    "type must not be null or blank",
                    Map.of("field", "type", "allowed", allowed()));
        }
        try {
            return LocationType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(
                    "type must be one of " + allowed(),
                    Map.of("field", "type", "value", raw, "allowed", allowed()));
        }
    }

    private static String allowed() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));
    }
}
