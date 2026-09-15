package io.tenoro.app.domain.model;

/**
 * Deterministic evaluation outcome derived from current stock versus {@code min}
 * and allocated versus required quantity. The four values are mutually exclusive
 * and exhaustive; {@code allocatedQuantity} never exceeds {@code requiredQuantity}.
 */
public enum ReplenishmentOutcome {
    NOT_REQUIRED,
    UNAVAILABLE,
    PLANNED,
    PARTIALLY_PLANNED;

    public static ReplenishmentOutcome derive(long currentStock, long min, long allocatedQuantity, long requiredQuantity) {
        if (currentStock >= min) {
            return NOT_REQUIRED;
        }
        if (allocatedQuantity == 0) {
            return UNAVAILABLE;
        }
        if (allocatedQuantity == requiredQuantity) {
            return PLANNED;
        }
        return PARTIALLY_PLANNED;
    }
}
