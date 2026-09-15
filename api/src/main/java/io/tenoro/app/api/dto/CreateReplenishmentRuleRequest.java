package io.tenoro.app.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

@Schema(
        name = "CreateReplenishmentRuleRequest",
        title = "Create Replenishment Rule Request",
        description = "Defines min/max replenishment thresholds for a SKU at a picking location",
        requiredProperties = {"sku", "locationCode", "min", "max"}
)
public record CreateReplenishmentRuleRequest(

        @Schema(description = "Case-sensitive product identifier. Must not be blank or have leading/trailing whitespace; not trimmed or normalized.", example = "SKU-100")
        @NotBlank(message = "sku must not be blank")
        String sku,

        @Schema(description = "Picking location code. Case-sensitive; must not be blank or have leading/trailing whitespace; not trimmed or normalized.", example = "PICK-01")
        @NotBlank(message = "locationCode must not be blank")
        String locationCode,

        @Schema(description = "Minimum on-hand quantity that still avoids replenishment. Must be 0 <= min <= max.", example = "20", format = "int64", minimum = "0")
        @NotNull(message = "min must not be null")
        @PositiveOrZero(message = "min must not be negative")
        Long min,

        @Schema(description = "Replenishment target level (not physical capacity). Must be 0 <= min <= max.", example = "100", format = "int64", minimum = "0")
        @NotNull(message = "max must not be null")
        @PositiveOrZero(message = "max must not be negative")
        Long max
) {
}
