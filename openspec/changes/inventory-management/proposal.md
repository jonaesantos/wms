## Why

The WMS needs a reliable inventory foundation before replenishment can be planned. Locations, physical stock, and atomic stock movement must have explicit behavior so later workflows can depend on consistent quantities.

## What Changes

- Add creation and deterministic listing of `PICKING` and `RESERVE` warehouse locations.
- Add absolute stock loading and deterministic stock lookup by SKU.
- Add atomic stock movement between distinct existing locations with checked arithmetic and no partial updates.
- Add strict identifier and quantity validation, structured API errors, and OpenAPI documentation aligned with runtime behavior.
- Add isolated domain tests and API integration tests, including conflict and concurrency scenarios.
- Keep replenishment rules, tasks, FSM, and the mandatory cross-feature seed in the separate planned `replenishment-workflow` change.
- Keep automatic replenishment, product catalogs, location capacity, and movement history out of scope.

## Capabilities

### New Capabilities

- `inventory-management`: Manage warehouse locations and physical SKU quantities, including atomic movement and observable API errors.

### Modified Capabilities

None.

## Impact

- Adds domain models, value objects, inbound services, outbound repository ports, and business exceptions in `domain`.
- Adds request, response, and error contracts in `api`.
- Adds REST controllers, exception mapping, in-memory adapters, shared synchronization, and Spring wiring in `infra`.
- Adds Bean Validation dependencies and tests across the domain and root application modules.
- Exposes `POST /locations`, `GET /locations`, `POST /stock`, `GET /stock`, and `POST /stock/move` below `/api/templates`.
