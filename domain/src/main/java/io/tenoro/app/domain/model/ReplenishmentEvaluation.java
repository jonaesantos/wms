package io.tenoro.app.domain.model;

import java.util.List;

/**
 * Observable result of evaluating a SKU at a picking location. Quantity names:
 * {@code targetQuantity} is the rule's {@code max}; {@code requiredQuantity} is
 * {@code max - currentStock} when replenishment is required, otherwise zero.
 */
public record ReplenishmentEvaluation(
        Sku sku,
        LocationCode locationCode,
        long currentStock,
        long min,
        long max,
        long targetQuantity,
        long requiredQuantity,
        long allocatedQuantity,
        long shortfall,
        ReplenishmentOutcome outcome,
        List<ReplenishmentTask> tasks
) {

    public ReplenishmentEvaluation {
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
    }

    public static ReplenishmentEvaluation of(
            ReplenishmentRule rule,
            long currentStock,
            long allocatedQuantity,
            List<ReplenishmentTask> tasks) {
        long targetQuantity = rule.targetQuantity();
        long requiredQuantity = rule.requiredQuantity(currentStock);
        long shortfall = Math.max(0L, requiredQuantity - allocatedQuantity);
        ReplenishmentOutcome outcome = ReplenishmentOutcome.derive(
                currentStock, rule.min(), allocatedQuantity, requiredQuantity);
        return new ReplenishmentEvaluation(
                rule.sku(),
                rule.locationCode(),
                currentStock,
                rule.min(),
                rule.max(),
                targetQuantity,
                requiredQuantity,
                allocatedQuantity,
                shortfall,
                outcome,
                tasks);
    }
}
