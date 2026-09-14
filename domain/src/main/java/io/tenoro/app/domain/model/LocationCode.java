package io.tenoro.app.domain.model;

/**
 * Case-sensitive warehouse location identifier value object. Rejects null,
 * empty, blank, or surrounding-whitespace values without silent normalization.
 */
public record LocationCode(String value) {

    public LocationCode {
        value = Identifiers.requireStrict("locationCode", value);
    }

    public static LocationCode of(String value) {
        return new LocationCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
