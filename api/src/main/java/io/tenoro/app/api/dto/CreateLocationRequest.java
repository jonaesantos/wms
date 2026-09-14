package io.tenoro.app.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Request to create a warehouse location.
 */
@Schema(
        name = "CreateLocationRequest",
        title = "Create Location Request",
        description = "Request object for creating a warehouse location",
        requiredProperties = {"code", "type"}
)
public record CreateLocationRequest(

        @Schema(
                description = "Unique, case-sensitive location code. Must not be blank or have leading/trailing whitespace; not trimmed or normalized.",
                example = "PICK-01",
                minLength = 1
        )
        @NotBlank(message = "code must not be blank")
        String code,

        @Schema(
                description = "Role of the location",
                example = "PICKING",
                allowableValues = {"PICKING", "RESERVE"}
        )
        @NotBlank(message = "type must not be blank")
        String type
) {
}
