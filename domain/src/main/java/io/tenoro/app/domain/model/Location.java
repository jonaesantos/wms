package io.tenoro.app.domain.model;

import io.tenoro.app.domain.exception.ValidationException;

import java.util.Map;

/**
 * A warehouse location identified by a unique, case-sensitive {@link LocationCode}
 * and a {@link LocationType}.
 */
public record Location(LocationCode code, LocationType type) {

    public Location {
        if (code == null) {
            throw new ValidationException("code must not be null", Map.of("field", "code", "reason", "null"));
        }
        if (type == null) {
            throw new ValidationException("type must not be null", Map.of("field", "type", "reason", "null"));
        }
    }

    public static Location of(String code, String type) {
        return new Location(LocationCode.of(code), LocationType.from(type));
    }
}
