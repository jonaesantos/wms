package io.tenoro.app.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(
        name = "ReplenishmentTaskResponse",
        title = "Replenishment Task Response",
        description = "A replenishment task moving a SKU from reserve to picking",
        requiredProperties = {"id", "sku", "fromLocation", "toLocation", "quantity", "status", "createdAt", "updatedAt"}
)
public record ReplenishmentTaskResponse(

        @Schema(description = "Task identifier (UUID)", example = "11111111-1111-1111-1111-111111111111", format = "uuid")
        UUID id,

        @Schema(description = "Case-sensitive product identifier", example = "SKU-100")
        String sku,

        @Schema(description = "Reserve origin location code", example = "RSV-01")
        String fromLocation,

        @Schema(description = "Picking destination location code", example = "PICK-01")
        String toLocation,

        @Schema(description = "Quantity to move", example = "60", format = "int64")
        long quantity,

        @Schema(description = "Task status", example = "OPEN", allowableValues = {"OPEN", "CONFIRMED", "CANCELLED"})
        String status,

        @Schema(description = "Creation timestamp", example = "2026-09-15T12:00:00Z")
        Instant createdAt,

        @Schema(description = "Last update timestamp", example = "2026-09-15T12:00:00Z")
        Instant updatedAt
) {
}
