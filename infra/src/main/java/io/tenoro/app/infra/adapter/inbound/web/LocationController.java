package io.tenoro.app.infra.adapter.inbound.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.tenoro.app.api.dto.ApiErrorResponse;
import io.tenoro.app.api.dto.CreateLocationRequest;
import io.tenoro.app.api.dto.LocationResponse;
import io.tenoro.app.domain.model.Location;
import io.tenoro.app.domain.port.inbound.LocationService;
import io.tenoro.app.infra.adapter.inbound.web.mappers.LocationResponseMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/locations")
@Tag(name = "Locations", description = "Warehouse location management")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @Operation(summary = "Create a warehouse location",
            description = "Creates a location with a unique, case-sensitive code and a PICKING or RESERVE type. Codes must not be blank or padded; they are not trimmed or normalized.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Location created",
                    content = @Content(schema = @Schema(implementation = LocationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid code or type",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Location code already exists",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<LocationResponse> createLocation(@Valid @RequestBody CreateLocationRequest request) {
        Location location = locationService.createLocation(request.code(), request.type());
        return ResponseEntity.status(HttpStatus.CREATED).body(LocationResponseMapper.fromDomain(location));
    }

    @Operation(summary = "List warehouse locations",
            description = "Returns all locations ordered by code ascending")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Locations returned",
                    content = @Content(schema = @Schema(implementation = LocationResponse.class)))
    })
    @GetMapping
    public ResponseEntity<List<LocationResponse>> listLocations() {
        List<LocationResponse> locations = locationService.listLocations().stream()
                .map(LocationResponseMapper::fromDomain)
                .toList();
        return ResponseEntity.ok(locations);
    }
}
