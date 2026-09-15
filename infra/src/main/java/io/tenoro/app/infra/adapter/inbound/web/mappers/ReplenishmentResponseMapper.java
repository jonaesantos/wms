package io.tenoro.app.infra.adapter.inbound.web.mappers;

import io.tenoro.app.api.dto.ReplenishmentEvaluationResponse;
import io.tenoro.app.api.dto.ReplenishmentRuleResponse;
import io.tenoro.app.api.dto.ReplenishmentTaskResponse;
import io.tenoro.app.domain.model.ReplenishmentEvaluation;
import io.tenoro.app.domain.model.ReplenishmentRule;
import io.tenoro.app.domain.model.ReplenishmentTask;

import java.util.List;

public final class ReplenishmentResponseMapper {

    private ReplenishmentResponseMapper() {
    }

    public static ReplenishmentRuleResponse fromRule(ReplenishmentRule rule) {
        return new ReplenishmentRuleResponse(
                rule.sku().value(),
                rule.locationCode().value(),
                rule.min(),
                rule.max());
    }

    public static ReplenishmentTaskResponse fromTask(ReplenishmentTask task) {
        return new ReplenishmentTaskResponse(
                task.id().value(),
                task.sku().value(),
                task.fromLocation().value(),
                task.toLocation().value(),
                task.quantity(),
                task.status().name(),
                task.createdAt(),
                task.updatedAt());
    }

    public static ReplenishmentEvaluationResponse fromEvaluation(ReplenishmentEvaluation evaluation) {
        List<ReplenishmentTaskResponse> tasks = evaluation.tasks().stream()
                .map(ReplenishmentResponseMapper::fromTask)
                .toList();
        return new ReplenishmentEvaluationResponse(
                evaluation.sku().value(),
                evaluation.locationCode().value(),
                evaluation.currentStock(),
                evaluation.min(),
                evaluation.max(),
                evaluation.targetQuantity(),
                evaluation.requiredQuantity(),
                evaluation.allocatedQuantity(),
                evaluation.shortfall(),
                evaluation.outcome().name(),
                tasks);
    }
}
