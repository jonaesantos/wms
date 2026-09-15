package io.tenoro.app.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(
        name = "EvaluateReplenishmentRequest",
        title = "Evaluate Replenishment Request",
        description = "Evaluates whether a SKU at a picking location needs replenishment",
        requiredProperties = {"sku", "locationCode"}
)
public record EvaluateReplenishmentRequest(

        @Schema(description = "Case-sensitive product identifier. Must not be blank or have leading/trailing whitespace; not trimmed or normalized.", example = "SKU-100")
        @NotBlank(message = "sku must not be blank")
        String sku,

        @Schema(description = "Picking location code. Case-sensitive; must not be blank or have leading/trailing whitespace; not trimmed or normalized.", example = "PICK-01")
        @NotBlank(message = "locationCode must not be blank")
        String locationCode
) {
}
