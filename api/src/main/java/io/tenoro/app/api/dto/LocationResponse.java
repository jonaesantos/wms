package io.tenoro.app.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Representation of a warehouse location.
 */
@Schema(
        name = "LocationResponse",
        title = "Location Response",
        description = "A warehouse location",
        requiredProperties = {"code", "type"}
)
public record LocationResponse(

        @Schema(description = "Unique, case-sensitive location code. Not trimmed or normalized.", example = "PICK-01")
        String code,

        @Schema(description = "Role of the location", example = "PICKING", allowableValues = {"PICKING", "RESERVE"})
        String type
) {
}
