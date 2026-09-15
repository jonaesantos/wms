## Context

See `proposal.md` for motivation and `specs/replenishment-workflow/spec.md` for observable behavior. The archived `inventory-management` capability already provides `Location`, `InventoryItem`, atomic `POST /stock/move` (`InventoryService.moveStock`), a single process-wide `ReentrantLock` shared through `InventoryConfiguration`, the `Sku`/`LocationCode` value objects, the `ApiErrorResponse` contract, and a centralized `@RestControllerAdvice` that maps validation, not-found, and conflict domain exceptions to `400`/`404`/`409`. This change adds replenishment rules and tasks on top of that foundation without modifying it, reusing the movement operation and the shared lock so replenishment confirmation stays atomic with physical stock.

## Goals / Non-Goals

**Goals:**

- Keep replenishment business rules in plain Java domain services, following the existing hexagonal placement (`domain`/`api`/`infra`).
- Reuse `inventory-management` as a dependency: the shared reentrant lock, `InventoryService.moveStock`, structured errors, and strict identifier/quantity validation, without duplicating any inventory rule.
- Make replenishment evaluation, allocation ordering, assignable-stock computation, and the task FSM deterministic and testable in isolation.
- Guarantee process-local atomicity for rule creation, evaluation, confirmation, and cancellation using the same critical section as inventory.

**Non-Goals:**

- Reversing confirmed tasks, partial confirmation, or automatic re-planning of stale tasks.
- Idempotency keys, advanced reserve balancing (for example distance or capacity aware), or the optional `POST /replenishment/scan`.
- Lots, expiry, pallets, zones, capacity, distance, or a full trazable movement history beyond `createdAt`/`updatedAt`.
- Database persistence; state remains in memory as allowed for the core scope.

## Decisions

### Model rules and tasks in the domain module

Add `ReplenishmentRule` (`Sku`, `LocationCode`, `min`, `max`), `ReplenishmentTask` (`id`, `Sku`, `fromLocation`, `toLocation`, `quantity`, `ReplenishmentTaskStatus`, `createdAt`, `updatedAt`), a `ReplenishmentTaskStatus` enum (`OPEN`, `CONFIRMED`, `CANCELLED`), and a `ReplenishmentEvaluation` result value object. Add inbound `ReplenishmentService` and outbound `ReplenishmentRuleRepository` and `ReplenishmentTaskRepository` ports, plus a `ReplenishmentDomainService`. DTOs live in `api`; controllers, mappers, in-memory adapters, the seed loader, and Spring wiring live in `infra`.

This mirrors the placement `inventory-management` established. Putting rules in controllers was rejected because it couples behavior to HTTP and prevents isolated domain tests, matching the existing inventory decision.

### Reuse the shared reentrant lock and the inventory movement operation

The `ReplenishmentDomainService` receives the same `Lock` bean already created in `InventoryConfiguration` and the `InventoryService` inbound port. Every public replenishment operation acquires the lock with `try/finally`. Confirmation calls `InventoryService.moveStock(...)` while holding the lock; because the lock is a `ReentrantLock`, the nested inventory call does not deadlock (this reuse was explicitly anticipated in the inventory design and configuration).

Introducing a second lock was rejected because cross-feature invariants (assignable stock derived from physical stock plus open tasks, and confirmation that both moves stock and flips state) must be atomic together. Re-implementing movement inside replenishment was rejected because `moveStock` is already the atomic base operation with checked arithmetic and the correct conflict codes.

### Trigger only strictly below minimum

Evaluation generates tasks only when `currentStock < min`. When `currentStock == min` or `currentStock > min` it generates nothing and returns a `200` result with `requiredQuantity` 0 and `shortfall` 0. Treating `== min` as a trigger was rejected because the minimum is the acceptable floor; being exactly at the floor does not require action.

### Use precise quantity names: target versus required

`targetQuantity = rule.max` is the level the picking location is brought back to. `requiredQuantity = rule.max - currentStock` is the planned replenishment amount. The evaluation result exposes `targetQuantity`, `requiredQuantity`, `allocatedQuantity`, and `shortfall`. We deliberately do not call `max - currentStock` the "target": that value is the required/planned quantity, and `max` is a replenishment target level, not physical capacity of the location. Overloading the word "target" for both was rejected because it hides the distinction between the goal level and the amount to move.

### Expose a deterministic evaluation outcome

The evaluation result carries an `outcome` enum so clients branch on a stable, self-describing value instead of reverse-engineering it from the quantity fields. The four outcomes are mutually exclusive and exhaustive: `NOT_REQUIRED` (`currentStock >= min`, no tasks), `UNAVAILABLE` (required but `allocatedQuantity == 0`), `PLANNED` (`allocatedQuantity == requiredQuantity`), and `PARTIALLY_PLANNED` (`0 < allocatedQuantity < requiredQuantity`). Because allocation never assigns more than `requiredQuantity`, `allocatedQuantity` can never exceed it, so those four cases cover every result. Returning only raw quantities was rejected because each client would re-derive the same classification and could disagree on edge cases such as "required but nothing available".

### Allocate greedily by assignable stock, deterministically

Allocation ranks `RESERVE` locations that hold the SKU by assignable stock descending and breaks ties by `locationCode` ascending, then greedily assigns `min(assignableStock, remainingRequired)` to each, creating one task per reserve drawn from and skipping zero-assignable reserves. Selecting the largest assignable reserve first reduces the number of tasks for the common case; the ascending-`locationCode` tie-break makes results reproducible for tests and operators. More elaborate balancing (spreading load, minimizing travel) was rejected as an explicit non-goal for this slice.

### Compute assignable stock dynamically; open tasks reserve without moving

Assignable stock for a `(reserve, sku)` pair is computed on demand as `max(0, physicalStock - sum(OPEN task quantities for that reserve+SKU across all pickings))`. `OPEN` tasks therefore reserve reserve-side stock without moving physical stock, preventing two evaluations from allocating the same units. Confirmed and cancelled tasks do not count: a confirmed task has already debited physical stock, and a cancelled task releases its reservation.

The result is clamped at zero so it is never negative. Physical stock can legitimately fall below the total of outstanding `OPEN` allocations (for example an external `POST /stock/move` drained the reserve after tasks were planned); in that case there is simply nothing assignable, not a negative amount. Confirmation revalidates physical stock, so those now-unbacked tasks fail with `INSUFFICIENT_STOCK` rather than moving stock they cannot cover.

Persisting an "assignable" number was rejected because it would need to be kept consistent with both physical stock and task state; deriving it keeps a single source of truth (physical stock plus open tasks) and matches the inventory decision to avoid redundant persisted state.

### Validate the picking location before the rule on evaluation

Evaluation targets a picking location, so it validates in a fixed order: the location exists (`404 LOCATION_NOT_FOUND`), the location is of type `PICKING` (`400 LOCATION_NOT_PICKING`), then a rule exists for the exact `sku` and `locationCode` (`404 RULE_NOT_FOUND`). A `RESERVE` location can never host a picking rule, so an evaluation aimed at one is a bad request rather than a missing rule; reporting `RULE_NOT_FOUND` there would hide the real problem. Checking existence before type keeps the "unknown vs. wrong type" distinction crisp, and checking the rule last means a `PICKING` location without a rule still surfaces `RULE_NOT_FOUND`. This mirrors the same ordered validation the rule-creation endpoint uses.

### Block re-evaluation while open tasks exist

If any `OPEN` task already exists for the same `sku` and picking `locationCode`, evaluation returns `409 REPLENISHMENT_IN_PROGRESS` and creates nothing. This keeps the picking-side trigger simple: because outstanding work for that picking is blocked, `currentStock` at the picking location can be read as plain physical stock without also modelling inbound open tasks. Auto-superseding or re-planning existing tasks was rejected as an explicit non-goal.

### Confirm revalidates and moves atomically; cancel only changes state

Confirmation, under the shared lock, validates in a fixed order: the task exists (`404 TASK_NOT_FOUND`), the task is `OPEN` (`409 TASK_NOT_OPEN`), the reserve still physically holds at least the task quantity (`409 INSUFFICIENT_STOCK`), and the destination would not overfill beyond its target (`409 DESTINATION_TARGET_EXCEEDED`, see next decision). Only then does it call `moveStock` from reserve to picking and flip the task to `CONFIRMED`, updating `updatedAt`. Any failing check leaves the task `OPEN` and moves nothing. Cancellation flips an `OPEN` task to `CANCELLED` and moves no stock; it also rejects terminal tasks with `409 TASK_NOT_OPEN` and unknown ids with `404 TASK_NOT_FOUND`.

Relying on the earlier evaluation's assignable check instead of revalidating at confirm time was rejected because physical stock can change between evaluation and confirmation; confirmation is the moment stock actually moves and must re-check.

### Reject stale confirmations that would overfill the destination

At confirmation time the system reloads the rule for the task's `sku` and picking `toLocation` and checks that `currentDestinationStock + task.quantity <= targetQuantity` (the rule's `max`). If it would exceed the target, confirmation returns `409 DESTINATION_TARGET_EXCEEDED`, leaves the task `OPEN`, and moves nothing. `max` is not treated as a universal physical capacity of the location (physical stock can legitimately exceed it through other operations), but a task that was *created to replenish toward `max`* should not be confirmed once it has become stale (for example because another replenishment already refilled the picking location) and would push the destination past the very target it was planned for. The remedy is operational, not automatic: the operator cancels the stale task and re-evaluates against current stock.

Silently clamping the moved quantity to fit the target was rejected because partial confirmation is an explicit non-goal and would move a different quantity than the task promises; auto-cancelling the stale task was rejected because it hides the conflict from the operator who may want to inspect why it went stale.

### FSM terminal states and no reversal

`OPEN → CONFIRMED` and `OPEN → CANCELLED` are the only transitions; `CONFIRMED` and `CANCELLED` are terminal and read-only. Confirming or cancelling a terminal task returns `409 TASK_NOT_OPEN`. Reversal of a confirmed task is deliberately not modelled: if an operator makes a mistake, the correction is a future feature (a compensating movement, an adjustment, or a trazable history), not an undo on this task. This keeps the current slice small and its invariants provable.

### Minimal traceability via timestamps

`ReplenishmentTask` carries `createdAt` (set at generation) and `updatedAt` (set on confirm/cancel) as minimal traceability. A full movement history is an explicit non-goal. Timestamps come from an injected `java.time.Clock` bean so tests can use a fixed clock and assert deterministic values.

### Task ids are UUIDs; malformed ids are validation errors

`ReplenishmentTaskId` wraps a UUID assigned with `UUID.randomUUID()` at generation. The confirm and cancel endpoints declare `{id}` as a UUID and parse it strictly before any lookup, so a malformed (non-UUID) id is a client mistake that returns `400 VALIDATION_ERROR`, while a syntactically valid UUID that matches no task is a missing resource that returns `404 TASK_NOT_FOUND`. Keeping the two distinct avoids masking a bad request as a missing resource and matches the strict-identifier stance the rest of the change inherits from `inventory-management`. Malformed ids reuse the existing `VALIDATION_ERROR` code rather than introducing a new one. Sequential or client-supplied ids were rejected: UUIDs need no coordination under the shared lock and carry no accidental ordering meaning.

### Chosen default HTTP statuses for cases the challenge leaves open

The challenge does not fix statuses for a few cases; these are decided here and documented:

- A rule targeting an existing but non-`PICKING` location returns `400 LOCATION_NOT_PICKING`. The request references a location that structurally cannot host a picking rule, so it is a bad request rather than a state conflict.
- Evaluating an existing but non-`PICKING` location returns `400 LOCATION_NOT_PICKING` for the same reason; evaluation validates location existence, then type, then rule existence.
- Evaluating a SKU/picking with no rule returns `404 RULE_NOT_FOUND`, treating the missing rule as a missing resource, consistent with `LOCATION_NOT_FOUND`.
- Confirming a stale task that would overfill the destination beyond its target returns `409 DESTINATION_TARGET_EXCEEDED`, treating the target overshoot as a state conflict resolvable by cancel and re-evaluate.
- A malformed (non-UUID) task id on confirm or cancel returns `400 VALIDATION_ERROR`, while a well-formed UUID matching no task returns `404 TASK_NOT_FOUND`.
- The evaluation endpoint returns `200`, not `201`, even when it creates tasks: it is an evaluation that may create zero-to-many tasks with no single new resource URL, consistent with the inventory decision to return `200` for `establishStock`.

### Idempotent in-memory seed on startup

A single startup loader (for example a `CommandLineRunner`) invokes the existing location and inventory services and the new rule service to load the challenge scenario into the in-memory repositories. It checks existence before creating each location, rule, and quant so re-running it within the same process/context creates no duplicates and preserves existing values. It creates no tasks. Because persistence is in-memory, the seed does not survive a restart; each fresh startup reloads it from a clean state. Loading through the domain services (not directly into repositories) keeps the seed subject to the same validation and lock as normal operations.

## Risks / Trade-offs

- [The global lock serializes unrelated replenishment and inventory operations] → Accept the throughput cost for a small in-memory single-process service; it is the same trade-off already accepted by `inventory-management` and can be replaced by database transactions if persistence changes.
- [Dynamically recomputing assignable stock scans open tasks on each evaluation] → Accept it for the small in-memory dataset; a persisted or indexed reservation could replace it later without changing observable behavior.
- [Physical stock can change between evaluation and confirmation, invalidating a planned task] → Confirmation revalidates physical stock and returns `409 INSUFFICIENT_STOCK` rather than moving a partial or negative quantity; stale tasks can be cancelled.
- [No reversal of a confirmed task] → Documented as out of scope; corrections are deferred to a future compensating-movement/adjustment/history feature.
- [In-memory state and seed disappear on restart] → Allowed for core scope and made obvious in the README.

## Migration Plan

No persisted data migration is needed. Add the replenishment slice alongside `inventory-management` and the User example, run domain and integration tests, then verify the five new API paths and the seed through OpenAPI and a local application run. Rollback consists of removing the new slice and its wiring because no external persisted state is introduced.
