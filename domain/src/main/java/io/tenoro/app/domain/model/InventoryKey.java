package io.tenoro.app.domain.model;

import io.tenoro.app.domain.exception.ValidationException;

import java.util.Map;

/**
 * Immutable composite key that addresses a single quant: one {@link Sku} at one
 * {@link LocationCode}. Used by the inventory repository to store and look up
 * quantities deterministically.
 */
public record InventoryKey(Sku sku, LocationCode locationCode) {

    public InventoryKey {
        if (sku == null) {
            throw new ValidationException("sku must not be null", Map.of("field", "sku", "reason", "null"));
        }
        if (locationCode == null) {
            throw new ValidationException(
                    "locationCode must not be null", Map.of("field", "locationCode", "reason", "null"));
        }
    }
}
