package io.tenoro.app.domain.port.inbound;

import io.tenoro.app.domain.model.ReplenishmentEvaluation;
import io.tenoro.app.domain.model.ReplenishmentRule;
import io.tenoro.app.domain.model.ReplenishmentTask;

import java.util.List;

/**
 * Inbound port for replenishment rules and tasks. Accepts raw request values so
 * identifier and quantity validation happens inside the domain.
 */
public interface ReplenishmentService {

    /**
     * Creates a replenishment rule for a SKU at an existing {@code PICKING} location.
     *
     * @throws io.tenoro.app.domain.exception.ValidationException if inputs are invalid or the location is not picking
     * @throws io.tenoro.app.domain.exception.NotFoundException   if the location does not exist
     * @throws io.tenoro.app.domain.exception.ConflictException   if a rule already exists for the pair
     */
    ReplenishmentRule createRule(String sku, String locationCode, Long min, Long max);

    /**
     * Evaluates a picking location against its rule and may generate {@code OPEN} tasks.
     */
    ReplenishmentEvaluation evaluate(String sku, String locationCode);

    /**
     * @return all tasks in deterministic order (createdAt then id).
     */
    List<ReplenishmentTask> listTasks();

    /**
     * Confirms an {@code OPEN} task: revalidates, moves stock, and marks {@code CONFIRMED}.
     */
    ReplenishmentTask confirm(String id);

    /**
     * Cancels an {@code OPEN} task without moving stock.
     */
    ReplenishmentTask cancel(String id);
}
