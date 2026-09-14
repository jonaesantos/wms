package io.tenoro.app.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request to move a positive quantity of a SKU between two distinct locations.
 */
@Schema(
        name = "MoveStockRequest",
        title = "Move Stock Request",
        description = "Moves stock of a SKU from one location to another atomically",
        requiredProperties = {"sku", "from", "to", "quantity"}
)
public record MoveStockRequest(

        @Schema(description = "Case-sensitive product identifier. Must not be blank or have leading/trailing whitespace; not trimmed or normalized.", example = "SKU-100")
        @NotBlank(message = "sku must not be blank")
        String sku,

        @Schema(description = "Origin location code. Case-sensitive; must not be blank or have leading/trailing whitespace; not trimmed or normalized.", example = "RSV-01")
        @NotBlank(message = "from must not be blank")
        String from,

        @Schema(description = "Destination location code. Case-sensitive; must not be blank or have leading/trailing whitespace; not trimmed or normalized.", example = "PICK-01")
        @NotBlank(message = "to must not be blank")
        String to,

        @Schema(description = "Positive quantity to move", example = "20", format = "int64", minimum = "1")
        @NotNull(message = "quantity must not be null")
        @Positive(message = "quantity must be positive")
        Long quantity
) {
}
