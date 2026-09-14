package io.tenoro.app.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Result of a successful stock movement: the moved quantity and the complete
 * resulting origin and destination quants.
 */
@Schema(
        name = "MoveStockResponse",
        title = "Move Stock Response",
        description = "Outcome of an atomic stock movement",
        requiredProperties = {"sku", "quantity", "from", "to"}
)
public record MoveStockResponse(

        @Schema(description = "Case-sensitive product identifier. Not trimmed or normalized.", example = "SKU-100")
        String sku,

        @Schema(description = "Quantity that was moved", example = "20", format = "int64")
        long quantity,

        @Schema(description = "Resulting origin quant")
        StockResponse from,

        @Schema(description = "Resulting destination quant")
        StockResponse to
) {
}
