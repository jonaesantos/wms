## Why

The `inventory-management` capability already provides reliable locations, physical stock, and atomic stock movement, but it deliberately left replenishment out of scope. The WMS still cannot detect when a picking location has fallen below its minimum and cannot plan or execute the moves that bring it back up. This change adds the replenishment rules and tasks that turn the consistent inventory foundation into the operational flow the challenge requires.

## What Changes

- Add creation of replenishment rules (`min`/`max` thresholds) for a SKU at a `PICKING` location, with strict validation and duplicate rejection.
- Add replenishment evaluation for a SKU at a picking location that validates the location exists and is `PICKING` before its rule, generates `OPEN` tasks only when `currentStock < min`, planning `requiredQuantity = max - currentStock` and drawing from reserve locations, and reports a deterministic `outcome` (`NOT_REQUIRED`, `UNAVAILABLE`, `PLANNED`, `PARTIALLY_PLANNED`).
- Add a dynamic assignable-stock rule, clamped at zero so it is never negative, so `OPEN` tasks reserve reserve-side stock without moving it, preventing over-allocation across concurrent evaluations.
- Add deterministic listing of replenishment tasks with UUID ids, status, and minimal traceability (`createdAt`/`updatedAt`); a malformed task id on confirm/cancel returns `400` while a well-formed unknown UUID returns `404`.
- Add task confirmation that revalidates reserve stock and the destination replenishment target, atomically moves stock via the existing inventory movement operation, and transitions `OPEN → CONFIRMED`, rejecting stale tasks that would overfill the destination beyond its target.
- Add task cancellation that transitions `OPEN → CANCELLED` without moving stock; `CONFIRMED` and `CANCELLED` are terminal, read-only states.
- Add an idempotent in-memory startup seed that preloads the mandatory challenge scenario (locations, stock, and rules).
- Add structured API errors, OpenAPI documentation, and domain plus integration tests covering the FSM, concurrency, assignable stock, partial replenishment, and atomic confirmation.

This change depends on `inventory-management` and reuses its atomic `moveStock` operation, its shared process-wide reentrant lock, its structured error contract, and its strict identifier/quantity validation instead of duplicating any inventory rule.

## Capabilities

### New Capabilities

- `replenishment-workflow`: Define replenishment rules, evaluate a picking location against them, generate and manage replenishment tasks through an `OPEN → CONFIRMED`/`CANCELLED` FSM with dynamic assignable stock and atomic confirmation, and preload the mandatory seed scenario.

### Modified Capabilities

None. `inventory-management` is consumed as a dependency and is not modified.

## Impact

- Adds domain models (`ReplenishmentRule`, `ReplenishmentTask`, task status enum, evaluation result), inbound/outbound ports, business exceptions, and a replenishment domain service in `domain`.
- Adds request, response, and error contracts in `api`.
- Adds REST controllers, mappers, in-memory rule/task adapters, a `Clock` bean, the startup seed loader, and Spring wiring in `infra`, reusing the existing shared lock and `InventoryService`.
- Exposes `POST /replenishment-rules`, `POST /replenishment/tasks`, `GET /replenishment/tasks`, `POST /replenishment/tasks/{id}/confirm`, and `POST /replenishment/tasks/{id}/cancel` below `/api/templates`.
- Adds new stable error codes (`RULE_ALREADY_EXISTS`, `RULE_NOT_FOUND`, `LOCATION_NOT_PICKING`, `TASK_NOT_FOUND`, `TASK_NOT_OPEN`, `REPLENISHMENT_IN_PROGRESS`, `DESTINATION_TARGET_EXCEEDED`) and reuses `INSUFFICIENT_STOCK` from inventory.
- Keeps confirmed-task reversal, partial confirmation, automatic re-planning, idempotency keys, advanced reserve balancing, lots/expiry/pallets/zones/capacity/distance, full movement history, and `POST /replenishment/scan` out of scope.
