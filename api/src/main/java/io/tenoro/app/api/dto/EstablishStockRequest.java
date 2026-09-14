package io.tenoro.app.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Request to establish the absolute quantity of a SKU at a location. The
 * quantity is a boxed, required {@code Long} so a missing value is rejected
 * rather than defaulting to zero.
 */
@Schema(
        name = "EstablishStockRequest",
        title = "Establish Stock Request",
        description = "Sets the absolute quantity of a SKU at an existing location",
        requiredProperties = {"sku", "locationCode", "quantity"}
)
public record EstablishStockRequest(

        @Schema(description = "Case-sensitive product identifier. Must not be blank or have leading/trailing whitespace; not trimmed or normalized.", example = "SKU-100")
        @NotBlank(message = "sku must not be blank")
        String sku,

        @Schema(description = "Case-sensitive location code. Must not be blank or have leading/trailing whitespace; not trimmed or normalized.", example = "PICK-01")
        @NotBlank(message = "locationCode must not be blank")
        String locationCode,

        @Schema(description = "Absolute non-negative quantity to set", example = "5", format = "int64", minimum = "0")
        @NotNull(message = "quantity must not be null")
        @PositiveOrZero(message = "quantity must not be negative")
        Long quantity
) {
}
