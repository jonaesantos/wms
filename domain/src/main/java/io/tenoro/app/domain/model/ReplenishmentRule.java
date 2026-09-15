package io.tenoro.app.domain.model;

import io.tenoro.app.domain.exception.ValidationException;

import java.util.Map;

/**
 * Min/max replenishment thresholds for one {@link Sku} at one picking
 * {@link LocationCode}. {@code max} is a replenishment target level, not
 * physical capacity. Invariants: {@code 0 <= min <= max}.
 */
public record ReplenishmentRule(Sku sku, LocationCode locationCode, long min, long max) {

    public ReplenishmentRule {
        if (sku == null) {
            throw new ValidationException("sku must not be null", Map.of("field", "sku", "reason", "null"));
        }
        if (locationCode == null) {
            throw new ValidationException(
                    "locationCode must not be null", Map.of("field", "locationCode", "reason", "null"));
        }
        if (min < 0) {
            throw new ValidationException(
                    "min must not be negative",
                    Map.of("field", "min", "value", min, "reason", "negative"));
        }
        if (max < 0) {
            throw new ValidationException(
                    "max must not be negative",
                    Map.of("field", "max", "value", max, "reason", "negative"));
        }
        if (min > max) {
            throw new ValidationException(
                    "min must be less than or equal to max",
                    Map.of("field", "min", "min", min, "max", max, "reason", "min-greater-than-max"));
        }
    }

    public static ReplenishmentRule of(Sku sku, LocationCode locationCode, long min, long max) {
        return new ReplenishmentRule(sku, locationCode, min, max);
    }

    public long targetQuantity() {
        return max;
    }

    /**
     * Planned replenishment amount: {@code max - currentStock} when stock is
     * strictly below {@code min}, otherwise zero (replenishment is not required).
     */
    public long requiredQuantity(long currentStock) {
        if (currentStock >= min) {
            return 0L;
        }
        return max - currentStock;
    }
}
