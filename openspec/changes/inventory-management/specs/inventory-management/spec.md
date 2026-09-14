## Purpose

Define reliable warehouse location and physical stock behavior that later replenishment workflows can use without observing invalid or partially applied inventory state.

## ADDED Requirements

### Requirement: Inventory identifiers are strict
The system SHALL treat `sku` and `locationCode` values as case-sensitive identifiers. It SHALL reject null, empty, blank, or surrounding-whitespace identifiers with HTTP `400` and SHALL NOT normalize them silently.

#### Scenario: Identifier has surrounding whitespace
- **WHEN** a request supplies `" PICK-01"`, `"PICK-01 "`, or an equivalently padded SKU
- **THEN** the system returns HTTP `400` with a structured validation error and persists no change

#### Scenario: Identifier case is significant
- **WHEN** locations `PICK-01` and `pick-01` are created with otherwise valid data
- **THEN** the system treats them as distinct location codes

### Requirement: Warehouse locations can be created
The system SHALL expose `POST /locations` to create a location with a unique `code` and a `type` of either `PICKING` or `RESERVE`. A successful request SHALL return HTTP `201` with the complete created location.

#### Scenario: Create a picking location
- **WHEN** a client posts a valid unused code and type `PICKING`
- **THEN** the system persists the location and returns HTTP `201` with its code and type

#### Scenario: Create a reserve location
- **WHEN** a client posts a valid unused code and type `RESERVE`
- **THEN** the system persists the location and returns HTTP `201` with its code and type

#### Scenario: Reject a duplicate location
- **WHEN** a client posts a location whose exact code already exists
- **THEN** the system returns HTTP `409` with error code `LOCATION_ALREADY_EXISTS` and does not replace the existing location

#### Scenario: Reject an invalid location type
- **WHEN** a client posts a null or unsupported location type
- **THEN** the system returns HTTP `400` with error code `VALIDATION_ERROR` and persists no location

#### Scenario: Concurrent duplicate location creation
- **WHEN** concurrent requests attempt to create the same unused location code
- **THEN** exactly one request creates the location and every competing request returns HTTP `409`

### Requirement: Warehouse locations can be listed deterministically
The system SHALL expose `GET /locations` and return HTTP `200` with all persisted locations ordered by code ascending.

#### Scenario: List existing locations
- **WHEN** multiple locations exist and a client gets `/locations`
- **THEN** the response contains each location exactly once in ascending code order

#### Scenario: List when no locations exist
- **WHEN** no locations have been persisted and a client gets `/locations`
- **THEN** the system returns HTTP `200` with an empty array

### Requirement: Stock can be established absolutely
The system SHALL expose `POST /stock` with required `sku`, `locationCode`, and `quantity` fields to establish the absolute `long` quantity for one SKU at one existing location. The quantity SHALL be zero or greater. A successful request SHALL upsert the quant and return HTTP `200` with `sku`, `locationCode`, and the resulting `quantity`.

#### Scenario: Establish stock for a new quant
- **WHEN** a client posts a valid SKU, existing location, and non-negative quantity for a quant that does not exist
- **THEN** the system persists that exact quantity and returns the complete quant with HTTP `200`

#### Scenario: Replace existing stock
- **WHEN** a client posts a quantity for an existing SKU and location quant
- **THEN** the system replaces rather than increments the quantity and returns the replacement with HTTP `200`

#### Scenario: Establish zero stock
- **WHEN** a client posts quantity zero for a valid SKU and existing location
- **THEN** the system persists a zero-valued quant and returns it with HTTP `200`

#### Scenario: Reject stock for an unknown location
- **WHEN** a client posts stock for a valid location code that does not exist
- **THEN** the system returns HTTP `404` with error code `LOCATION_NOT_FOUND` and persists no quant

#### Scenario: Reject a negative stock quantity
- **WHEN** a client posts a negative quantity
- **THEN** the system returns HTTP `400` with error code `VALIDATION_ERROR` and preserves existing stock

#### Scenario: Reject a non-integral or out-of-range quantity
- **WHEN** a client supplies a decimal value or a value outside the signed `long` range
- **THEN** the system returns HTTP `400` with a structured validation error and preserves existing stock

### Requirement: Stock can be queried by SKU
The system SHALL expose `GET /stock?sku={sku}` and return HTTP `200` with only persisted quants for the exact SKU, ordered by `locationCode` ascending. It SHALL NOT fabricate zero-valued rows for locations without a persisted quant.

#### Scenario: Query a SKU stored in multiple locations
- **WHEN** persisted quants for the requested SKU exist in multiple locations
- **THEN** the response contains their `sku`, `locationCode`, and `quantity` in ascending location-code order

#### Scenario: Query an unknown SKU
- **WHEN** no quant exists for the requested SKU
- **THEN** the system returns HTTP `200` with an empty array

#### Scenario: Query with an invalid SKU
- **WHEN** the `sku` query parameter is missing, blank, or has surrounding whitespace
- **THEN** the system returns HTTP `400` with error code `VALIDATION_ERROR`

### Requirement: Stock movement is atomic
The system SHALL expose `POST /stock/move` with required `sku`, `from`, `to`, and `quantity` fields to move a positive `long` quantity of one SKU between two distinct existing locations. It SHALL debit the origin and credit the destination as one operation, return HTTP `200` with the moved quantity and both resulting quants, and never persist a negative quantity or a partial movement.

#### Scenario: Move stock successfully
- **WHEN** the origin has sufficient stock and the destination addition fits in a signed `long`
- **THEN** the system debits and credits the requested quantity and returns both resulting quantities with HTTP `200`

#### Scenario: Destination quant does not yet exist
- **WHEN** a valid movement targets an existing location with no persisted quant for the SKU
- **THEN** the system treats destination stock as zero, creates the credited quant, and completes the movement

#### Scenario: Origin quant does not exist
- **WHEN** a movement originates at an existing location with no persisted quant for the SKU
- **THEN** the system treats origin stock as zero, returns HTTP `409` with error code `INSUFFICIENT_STOCK`, and does not change destination stock

#### Scenario: Origin stock is insufficient
- **WHEN** the origin quantity is less than the requested movement quantity
- **THEN** the system returns HTTP `409` with error code `INSUFFICIENT_STOCK` and preserves both quants

#### Scenario: Reject a non-positive movement
- **WHEN** movement quantity is zero or negative
- **THEN** the system returns HTTP `400` with error code `VALIDATION_ERROR` and preserves both quants

#### Scenario: Reject movement to the same location
- **WHEN** origin and destination location codes are equal
- **THEN** the system returns HTTP `400` with error code `VALIDATION_ERROR` and preserves stock

#### Scenario: Reject movement with an unknown location
- **WHEN** the origin or destination location does not exist
- **THEN** the system returns HTTP `404` with error code `LOCATION_NOT_FOUND` and persists no stock change

#### Scenario: Reject destination overflow
- **WHEN** crediting the destination would exceed the maximum signed `long` value
- **THEN** the system returns HTTP `409` with error code `STOCK_OVERFLOW` and preserves both quants

### Requirement: Concurrent inventory operations preserve consistency
The system SHALL serialize public location and inventory operations through one shared process-wide critical section so that compound checks and writes behave atomically within one application process.

#### Scenario: Concurrent movements compete for origin stock
- **WHEN** concurrent valid requests attempt to move more combined stock than one origin contains
- **THEN** only movements supported by the serialized stock state succeed, all others return HTTP `409`, total stock is conserved, and no quant becomes negative

#### Scenario: Query overlaps a movement
- **WHEN** a stock query executes concurrently with a movement
- **THEN** the query observes either the complete state before the movement or the complete state after it, never a debited origin with an uncredited destination

#### Scenario: Concurrent stock replacements
- **WHEN** concurrent requests establish different quantities for the same SKU and location
- **THEN** each request applies atomically and the final quantity equals one complete submitted value

### Requirement: Expected API errors are structured
Every expected inventory API failure SHALL return JSON containing a stable `code`, a human-readable `message`, and an object-valued `details`. Malformed JSON, invalid enums, missing required values, decimal or out-of-range integer quantities, and Bean Validation failures SHALL return HTTP `400` rather than HTTP `500`. Missing numeric fields SHALL NOT default to zero. Documented OpenAPI constraints and response statuses SHALL match runtime behavior.

#### Scenario: Business conflict response
- **WHEN** a duplicate or insufficient-stock conflict occurs
- **THEN** the response uses HTTP `409` and contains `code`, `message`, and `details`

#### Scenario: Missing resource response
- **WHEN** a request references an unknown but syntactically valid location
- **THEN** the response uses HTTP `404` and contains `code`, `message`, and `details`

#### Scenario: Malformed request response
- **WHEN** a client sends malformed JSON, an invalid enum, or a missing required field
- **THEN** the response uses HTTP `400` with error code `VALIDATION_ERROR` and contains no generic server error
