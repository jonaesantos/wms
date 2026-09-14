package io.tenoro.app.infra.adapter.inbound.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.tenoro.app.api.dto.ApiErrorResponse;
import io.tenoro.app.api.dto.EstablishStockRequest;
import io.tenoro.app.api.dto.MoveStockRequest;
import io.tenoro.app.api.dto.MoveStockResponse;
import io.tenoro.app.api.dto.StockResponse;
import io.tenoro.app.domain.model.InventoryItem;
import io.tenoro.app.domain.model.StockMovementResult;
import io.tenoro.app.domain.port.inbound.InventoryService;
import io.tenoro.app.infra.adapter.inbound.web.mappers.StockResponseMapper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/stock")
@Tag(name = "Stock", description = "Physical stock establishment, query, and movement")
public class StockController {

    private final InventoryService inventoryService;

    public StockController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Operation(summary = "Establish absolute stock",
            description = "Sets (replaces) the absolute quantity of a SKU at an existing location. sku and locationCode are case-sensitive and are not trimmed.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resulting quant",
                    content = @Content(schema = @Schema(implementation = StockResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid identifiers or quantity",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Location does not exist",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<StockResponse> establishStock(@Valid @RequestBody EstablishStockRequest request) {
        InventoryItem item = inventoryService.establishStock(
                request.sku(), request.locationCode(), request.quantity());
        return ResponseEntity.ok(StockResponseMapper.fromDomain(item));
    }

    @Operation(summary = "Query stock by SKU",
            description = "Returns only persisted quants for the exact SKU, ordered by location code ascending. Only the sku query parameter is supported; location filtering is intentionally out of scope.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matching quants (possibly empty)",
                    content = @Content(schema = @Schema(implementation = StockResponse.class))),
            @ApiResponse(responseCode = "400", description = "Missing or invalid SKU",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<List<StockResponse>> queryStock(
            @Parameter(
                    description = "Case-sensitive product identifier. Must not be blank or have leading/trailing whitespace; not trimmed or normalized. Location filtering is not supported.",
                    required = true,
                    example = "SKU-100")
            @RequestParam("sku") String sku) {
        List<StockResponse> quants = inventoryService.findStockBySku(sku).stream()
                .map(StockResponseMapper::fromDomain)
                .toList();
        return ResponseEntity.ok(quants);
    }

    @Operation(summary = "Move stock between locations",
            description = "Atomically moves a positive quantity of a SKU between two distinct existing locations. sku, from, and to are case-sensitive identifiers and are not trimmed.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Moved quantity and resulting quants",
                    content = @Content(schema = @Schema(implementation = MoveStockResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid identifiers, non-positive quantity, or same location",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Origin or destination location does not exist",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Insufficient origin stock or destination overflow",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/move")
    public ResponseEntity<MoveStockResponse> moveStock(@Valid @RequestBody MoveStockRequest request) {
        StockMovementResult result = inventoryService.moveStock(
                request.sku(), request.from(), request.to(), request.quantity());
        return ResponseEntity.ok(StockResponseMapper.fromMovement(result));
    }
}
