package io.tenoro.app.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(
        name = "ReplenishmentEvaluationResponse",
        title = "Replenishment Evaluation Response",
        description = "Result of evaluating a SKU at a picking location. targetQuantity is the rule max; requiredQuantity is max minus currentStock when replenishment is required.",
        requiredProperties = {
                "sku", "locationCode", "currentStock", "min", "max",
                "targetQuantity", "requiredQuantity", "allocatedQuantity", "shortfall", "outcome", "tasks"
        }
)
public record ReplenishmentEvaluationResponse(

        @Schema(description = "Case-sensitive product identifier", example = "SKU-100")
        String sku,

        @Schema(description = "Picking location code", example = "PICK-01")
        String locationCode,

        @Schema(description = "Current physical stock at the picking location", example = "5", format = "int64")
        long currentStock,

        @Schema(description = "Rule minimum", example = "20", format = "int64")
        long min,

        @Schema(description = "Rule maximum / replenishment target level", example = "100", format = "int64")
        long max,

        @Schema(description = "Target stock level to reach (rule.max)", example = "100", format = "int64")
        long targetQuantity,

        @Schema(description = "Planned replenishment amount (max - currentStock when below min, otherwise 0)", example = "95", format = "int64")
        long requiredQuantity,

        @Schema(description = "Quantity allocated across generated OPEN tasks", example = "95", format = "int64")
        long allocatedQuantity,

        @Schema(description = "Unmet remainder when allocation is partial", example = "0", format = "int64")
        long shortfall,

        @Schema(description = "Deterministic evaluation outcome",
                example = "PLANNED",
                allowableValues = {"NOT_REQUIRED", "UNAVAILABLE", "PLANNED", "PARTIALLY_PLANNED"})
        String outcome,

        @Schema(description = "Generated OPEN tasks, possibly empty")
        List<ReplenishmentTaskResponse> tasks
) {
}
