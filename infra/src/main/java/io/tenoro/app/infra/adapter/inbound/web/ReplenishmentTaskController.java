package io.tenoro.app.infra.adapter.inbound.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.tenoro.app.api.dto.ApiErrorResponse;
import io.tenoro.app.api.dto.EvaluateReplenishmentRequest;
import io.tenoro.app.api.dto.ReplenishmentEvaluationResponse;
import io.tenoro.app.api.dto.ReplenishmentTaskResponse;
import io.tenoro.app.domain.model.ReplenishmentEvaluation;
import io.tenoro.app.domain.model.ReplenishmentTask;
import io.tenoro.app.domain.port.inbound.ReplenishmentService;
import io.tenoro.app.infra.adapter.inbound.web.mappers.ReplenishmentResponseMapper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/replenishment/tasks")
@Tag(name = "Replenishment Tasks", description = "Evaluate, list, confirm, and cancel replenishment tasks")
public class ReplenishmentTaskController {

    private final ReplenishmentService replenishmentService;

    public ReplenishmentTaskController(ReplenishmentService replenishmentService) {
        this.replenishmentService = replenishmentService;
    }

    @Operation(summary = "Evaluate replenishment for a picking location",
            description = "Validates location existence, then PICKING type, then rule existence. Generates OPEN tasks only when currentStock < min. Returns 200 even when no tasks are created. targetQuantity is rule.max; requiredQuantity is max - currentStock when replenishment is required.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Evaluation result",
                    content = @Content(schema = @Schema(implementation = ReplenishmentEvaluationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid identifiers or non-picking location",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Location or rule not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Open tasks already exist for this SKU and picking location",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<ReplenishmentEvaluationResponse> evaluate(
            @Valid @RequestBody EvaluateReplenishmentRequest request) {
        ReplenishmentEvaluation evaluation = replenishmentService.evaluate(request.sku(), request.locationCode());
        return ResponseEntity.ok(ReplenishmentResponseMapper.fromEvaluation(evaluation));
    }

    @Operation(summary = "List replenishment tasks",
            description = "Returns all tasks ordered by createdAt then id, including UUID id, status, and timestamps.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tasks (possibly empty)",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = ReplenishmentTaskResponse.class))))
    })
    @GetMapping
    public ResponseEntity<List<ReplenishmentTaskResponse>> listTasks() {
        List<ReplenishmentTaskResponse> tasks = replenishmentService.listTasks().stream()
                .map(ReplenishmentResponseMapper::fromTask)
                .toList();
        return ResponseEntity.ok(tasks);
    }

    @Operation(summary = "Confirm a replenishment task",
            description = "Confirms an OPEN task: revalidates reserve stock and destination target, moves stock atomically via the inventory movement operation, and marks the task CONFIRMED. A malformed id is 400; a well-formed unknown UUID is 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Confirmed task",
                    content = @Content(schema = @Schema(implementation = ReplenishmentTaskResponse.class))),
            @ApiResponse(responseCode = "400", description = "Malformed task id",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Task not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Task not OPEN, insufficient reserve stock, or destination would exceed target",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/{id}/confirm")
    public ResponseEntity<ReplenishmentTaskResponse> confirm(
            @Parameter(description = "Task UUID", required = true, example = "11111111-1111-1111-1111-111111111111",
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable String id) {
        ReplenishmentTask task = replenishmentService.confirm(id);
        return ResponseEntity.ok(ReplenishmentResponseMapper.fromTask(task));
    }

    @Operation(summary = "Cancel a replenishment task",
            description = "Cancels an OPEN task without moving stock, releasing its reserved assignable stock. A malformed id is 400; a well-formed unknown UUID is 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cancelled task",
                    content = @Content(schema = @Schema(implementation = ReplenishmentTaskResponse.class))),
            @ApiResponse(responseCode = "400", description = "Malformed task id",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Task not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Task is not OPEN",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ReplenishmentTaskResponse> cancel(
            @Parameter(description = "Task UUID", required = true, example = "11111111-1111-1111-1111-111111111111",
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable String id) {
        ReplenishmentTask task = replenishmentService.cancel(id);
        return ResponseEntity.ok(ReplenishmentResponseMapper.fromTask(task));
    }
}
