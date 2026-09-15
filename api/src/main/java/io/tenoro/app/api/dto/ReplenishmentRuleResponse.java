package io.tenoro.app.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        name = "ReplenishmentRuleResponse",
        title = "Replenishment Rule Response",
        description = "A replenishment rule for a SKU at a picking location",
        requiredProperties = {"sku", "locationCode", "min", "max"}
)
public record ReplenishmentRuleResponse(

        @Schema(description = "Case-sensitive product identifier", example = "SKU-100")
        String sku,

        @Schema(description = "Picking location code", example = "PICK-01")
        String locationCode,

        @Schema(description = "Minimum threshold", example = "20", format = "int64")
        long min,

        @Schema(description = "Replenishment target level", example = "100", format = "int64")
        long max
) {
}
