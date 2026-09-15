package io.tenoro.app.infra.adapter.inbound.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.tenoro.app.api.dto.ApiErrorResponse;
import io.tenoro.app.api.dto.CreateReplenishmentRuleRequest;
import io.tenoro.app.api.dto.ReplenishmentRuleResponse;
import io.tenoro.app.domain.model.ReplenishmentRule;
import io.tenoro.app.domain.port.inbound.ReplenishmentService;
import io.tenoro.app.infra.adapter.inbound.web.mappers.ReplenishmentResponseMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/replenishment-rules")
@Tag(name = "Replenishment Rules", description = "Min/max replenishment thresholds for a SKU at a picking location")
public class ReplenishmentRuleController {

    private final ReplenishmentService replenishmentService;

    public ReplenishmentRuleController(ReplenishmentService replenishmentService) {
        this.replenishmentService = replenishmentService;
    }

    @Operation(summary = "Create a replenishment rule",
            description = "Defines min/max thresholds for a SKU at an existing PICKING location. sku and locationCode are case-sensitive and are not trimmed. max is a replenishment target level, not physical capacity.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Rule created",
                    content = @Content(schema = @Schema(implementation = ReplenishmentRuleResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid identifiers, thresholds, or non-picking location",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Location does not exist",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "A rule already exists for the SKU and location",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<ReplenishmentRuleResponse> createRule(
            @Valid @RequestBody CreateReplenishmentRuleRequest request) {
        ReplenishmentRule rule = replenishmentService.createRule(
                request.sku(), request.locationCode(), request.min(), request.max());
        return ResponseEntity.status(HttpStatus.CREATED).body(ReplenishmentResponseMapper.fromRule(rule));
    }
}
