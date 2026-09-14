package io.tenoro.app.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A quant: the quantity of a SKU at a single location.
 */
@Schema(
        name = "StockResponse",
        title = "Stock Response",
        description = "Quantity of a SKU at a single location",
        requiredProperties = {"sku", "locationCode", "quantity"}
)
public record StockResponse(

        @Schema(description = "Case-sensitive product identifier. Not trimmed or normalized.", example = "SKU-100")
        String sku,

        @Schema(description = "Case-sensitive location code. Not trimmed or normalized.", example = "PICK-01")
        String locationCode,

        @Schema(description = "Quantity at the location", example = "5", format = "int64")
        long quantity
) {
}
