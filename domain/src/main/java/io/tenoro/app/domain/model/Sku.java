package io.tenoro.app.domain.model;

/**
 * Case-sensitive product identifier value object. Rejects null, empty, blank,
 * or surrounding-whitespace values without silent normalization.
 */
public record Sku(String value) {

    public Sku {
        value = Identifiers.requireStrict("sku", value);
    }

    public static Sku of(String value) {
        return new Sku(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
