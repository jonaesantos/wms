package io.tenoro.app.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Structured error contract for expected WMS API failures. Every expected error
 * carries a stable {@code code}, a human-readable {@code message}, and an
 * object-valued {@code details} map.
 */
@Schema(
        name = "ApiErrorResponse",
        title = "API Error Response",
        description = "Structured error returned for expected WMS API failures",
        requiredProperties = {"code", "message", "details"}
)
public record ApiErrorResponse(
        @Schema(description = "Stable, machine-readable error code", example = "VALIDATION_ERROR")
        String code,

        @Schema(description = "Human-readable description of the failure", example = "quantity must not be negative")
        String message,

        @Schema(description = "Object-valued map with structured error context", example = "{\"field\":\"quantity\"}")
        Map<String, Object> details
) {
    public ApiErrorResponse {
        details = details == null ? Map.of() : details;
    }
}
