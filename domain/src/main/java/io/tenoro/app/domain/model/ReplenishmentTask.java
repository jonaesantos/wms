package io.tenoro.app.domain.model;

import io.tenoro.app.domain.exception.ValidationException;

import java.time.Instant;
import java.util.Map;

/**
 * A planned or completed replenishment movement of one {@link Sku} from a
 * reserve location to a picking location. {@code OPEN} tasks reserve assignable
 * stock without moving physical stock.
 */
public record ReplenishmentTask(
        ReplenishmentTaskId id,
        Sku sku,
        LocationCode fromLocation,
        LocationCode toLocation,
        long quantity,
        ReplenishmentTaskStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public ReplenishmentTask {
        if (id == null) {
            throw new ValidationException("id must not be null", Map.of("field", "id", "reason", "null"));
        }
        if (sku == null) {
            throw new ValidationException("sku must not be null", Map.of("field", "sku", "reason", "null"));
        }
        if (fromLocation == null) {
            throw new ValidationException(
                    "fromLocation must not be null", Map.of("field", "fromLocation", "reason", "null"));
        }
        if (toLocation == null) {
            throw new ValidationException(
                    "toLocation must not be null", Map.of("field", "toLocation", "reason", "null"));
        }
        if (quantity <= 0) {
            throw new ValidationException(
                    "quantity must be positive",
                    Map.of("field", "quantity", "value", quantity));
        }
        if (status == null) {
            throw new ValidationException("status must not be null", Map.of("field", "status", "reason", "null"));
        }
        if (createdAt == null) {
            throw new ValidationException(
                    "createdAt must not be null", Map.of("field", "createdAt", "reason", "null"));
        }
        if (updatedAt == null) {
            throw new ValidationException(
                    "updatedAt must not be null", Map.of("field", "updatedAt", "reason", "null"));
        }
    }

    public static ReplenishmentTask open(
            Sku sku,
            LocationCode fromLocation,
            LocationCode toLocation,
            long quantity,
            Instant at) {
        ReplenishmentTaskId id = ReplenishmentTaskId.generate();
        return new ReplenishmentTask(id, sku, fromLocation, toLocation, quantity,
                ReplenishmentTaskStatus.OPEN, at, at);
    }

    public ReplenishmentTask withStatus(ReplenishmentTaskStatus newStatus, Instant updatedAt) {
        return new ReplenishmentTask(id, sku, fromLocation, toLocation, quantity, newStatus, createdAt, updatedAt);
    }
}
