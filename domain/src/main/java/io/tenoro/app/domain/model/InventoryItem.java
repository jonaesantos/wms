package io.tenoro.app.domain.model;

import io.tenoro.app.domain.exception.ValidationException;

import java.util.Map;

/**
 * A quant: how much of a {@link Sku} is physically present at a {@link LocationCode}.
 * The quantity is a non-negative {@code long}; zero is a valid, persisted state.
 */
public record InventoryItem(Sku sku, LocationCode locationCode, long quantity) {

    public InventoryItem {
        if (sku == null) {
            throw new ValidationException("sku must not be null", Map.of("field", "sku", "reason", "null"));
        }
        if (locationCode == null) {
            throw new ValidationException(
                    "locationCode must not be null", Map.of("field", "locationCode", "reason", "null"));
        }
        if (quantity < 0) {
            throw new ValidationException(
                    "quantity must not be negative",
                    Map.of("field", "quantity", "value", quantity, "reason", "negative"));
        }
    }

    public static InventoryItem of(Sku sku, LocationCode locationCode, long quantity) {
        return new InventoryItem(sku, locationCode, quantity);
    }

    public InventoryKey key() {
        return new InventoryKey(sku, locationCode);
    }

    public InventoryItem withQuantity(long newQuantity) {
        return new InventoryItem(sku, locationCode, newQuantity);
    }
}
