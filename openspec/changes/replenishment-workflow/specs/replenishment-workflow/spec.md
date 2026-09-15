## Purpose

Define how the WMS plans and executes picking replenishment: replenishment rules, evaluation of a picking location against them, generation and lifecycle of replenishment tasks with dynamic assignable stock, and the mandatory seed scenario, all built on the atomic stock operations of `inventory-management`.

## ADDED Requirements

### Requirement: Replenishment identifiers are strict
The system SHALL treat `sku` and `locationCode` values on every replenishment endpoint as case-sensitive identifiers, consistent with `inventory-management`. It SHALL reject null, empty, blank, or surrounding-whitespace identifiers with HTTP `400` and SHALL NOT normalize them silently.

#### Scenario: Identifier has surrounding whitespace
- **WHEN** a replenishment request supplies `" SKU-100"`, `"PICK-01 "`, or an equivalently padded identifier
- **THEN** the system returns HTTP `400` with error code `VALIDATION_ERROR` and persists no change

#### Scenario: Identifier case is significant
- **WHEN** a rule is created for `SKU-100` and evaluation is requested for `sku-100`
- **THEN** the system treats them as distinct SKUs and does not reuse the rule

### Requirement: Replenishment rules can be created
The system SHALL expose `POST /replenishment-rules` with required `sku`, `locationCode`, `min`, and `max` fields to define replenishment thresholds for a SKU at an existing `PICKING` location. It SHALL require `0 <= min <= max`, reject a location that is not `PICKING`, and reject a second rule for the same `sku` and `locationCode`. A successful request SHALL return HTTP `201` with the complete created rule.

#### Scenario: Create a valid rule
- **WHEN** a client posts a valid unused `sku` and existing `PICKING` location with `0 <= min <= max`
- **THEN** the system persists the rule and returns HTTP `201` with its `sku`, `locationCode`, `min`, and `max`

#### Scenario: Reject a duplicate rule
- **WHEN** a client posts a rule whose exact `sku` and `locationCode` already have a rule
- **THEN** the system returns HTTP `409` with error code `RULE_ALREADY_EXISTS` and does not replace the existing rule

#### Scenario: Reject a rule on a non-picking location
- **WHEN** a client posts a rule whose location exists but is of type `RESERVE`
- **THEN** the system returns HTTP `400` with error code `LOCATION_NOT_PICKING` and persists no rule

#### Scenario: Reject a rule on an unknown location
- **WHEN** a client posts a rule for a syntactically valid location code that does not exist
- **THEN** the system returns HTTP `404` with error code `LOCATION_NOT_FOUND` and persists no rule

#### Scenario: Reject min greater than max
- **WHEN** a client posts a rule with `min` greater than `max`
- **THEN** the system returns HTTP `400` with error code `VALIDATION_ERROR` and persists no rule

#### Scenario: Reject a negative threshold
- **WHEN** a client posts a rule with a negative `min` or `max`
- **THEN** the system returns HTTP `400` with error code `VALIDATION_ERROR` and persists no rule

#### Scenario: Reject a missing threshold
- **WHEN** a client omits `min` or `max`
- **THEN** the system returns HTTP `400` with error code `VALIDATION_ERROR` and does not default the missing value to zero

### Requirement: Replenishment evaluation generates tasks only below minimum
The system SHALL expose `POST /replenishment/tasks` with required `sku` and `locationCode` fields to evaluate a picking location against its rule. The `locationCode` MUST identify a `PICKING` location. The system SHALL validate the request in this order: first that the location exists (else HTTP `404` `LOCATION_NOT_FOUND`), second that the location is of type `PICKING` (else HTTP `400` `LOCATION_NOT_PICKING`), and third that a rule for the exact `sku` and `locationCode` exists (else HTTP `404` `RULE_NOT_FOUND`). The system SHALL generate replenishment tasks only when the current physical stock at the picking location is strictly less than `min`; when current stock equals or exceeds `min` it SHALL generate no tasks. A successful evaluation SHALL return HTTP `200` with an evaluation result that exposes `sku`, `locationCode`, `currentStock`, `min`, `max`, `targetQuantity`, `requiredQuantity`, `allocatedQuantity`, `shortfall`, `outcome`, and the generated tasks.

#### Scenario: Current stock below minimum generates tasks
- **WHEN** the rule is `SKU-100` at `PICK-01` with `min` 20 and `max` 100 and current stock is 5
- **THEN** the system returns HTTP `200`, generates one or more `OPEN` tasks, and reports `requiredQuantity` 95

#### Scenario: Current stock equal to minimum generates nothing
- **WHEN** the current stock at the picking location equals `min`
- **THEN** the system returns HTTP `200`, generates no tasks, and reports `requiredQuantity` 0 with `shortfall` 0

#### Scenario: Current stock above minimum generates nothing
- **WHEN** the current stock at the picking location is greater than `min`
- **THEN** the system returns HTTP `200` and generates no tasks

#### Scenario: Evaluate without a rule
- **WHEN** no rule exists for the exact `sku` and `locationCode`
- **THEN** the system returns HTTP `404` with error code `RULE_NOT_FOUND` and generates no tasks

#### Scenario: Evaluate an unknown location
- **WHEN** the `locationCode` is syntactically valid but does not exist
- **THEN** the system returns HTTP `404` with error code `LOCATION_NOT_FOUND` and generates no tasks

#### Scenario: Evaluate a non-picking location
- **WHEN** the `locationCode` exists but is of type `RESERVE`
- **THEN** the system returns HTTP `400` with error code `LOCATION_NOT_PICKING` and generates no tasks

#### Scenario: Validation order for evaluation
- **WHEN** an evaluation request could fail more than one check
- **THEN** the system reports the failures in order: location existence (`404 LOCATION_NOT_FOUND`) before location type (`400 LOCATION_NOT_PICKING`) before rule existence (`404 RULE_NOT_FOUND`)

#### Scenario: Evaluate while open tasks already exist
- **WHEN** at least one `OPEN` task already exists for the same `sku` and picking `locationCode`
- **THEN** the system returns HTTP `409` with error code `REPLENISHMENT_IN_PROGRESS` and generates no additional tasks

### Requirement: Replenishment quantities use target and required semantics
The system SHALL compute `targetQuantity` as the rule's `max` (the stock level the picking location is brought back to) and `requiredQuantity` as `max - currentStock` (the planned replenishment amount). It SHALL NOT label `max - currentStock` as the target, and `max` SHALL be interpreted as a replenishment target level, not physical capacity.

#### Scenario: Result distinguishes target from required
- **WHEN** the rule is `max` 100 and current stock is 5
- **THEN** the evaluation result reports `targetQuantity` 100 and `requiredQuantity` 95

### Requirement: Evaluation reports a deterministic outcome
The evaluation result SHALL expose an `outcome` drawn from a fixed, mutually exclusive, and exhaustive set derived from `currentStock`/`min` and `allocatedQuantity`/`requiredQuantity`, so clients can branch on a stable value instead of inferring it from quantities. The `outcome` SHALL be `NOT_REQUIRED` when `currentStock >= min`; `UNAVAILABLE` when replenishment is required but `allocatedQuantity` is 0; `PLANNED` when `allocatedQuantity` equals `requiredQuantity`; and `PARTIALLY_PLANNED` when `0 < allocatedQuantity < requiredQuantity`. `allocatedQuantity` SHALL never exceed `requiredQuantity`.

#### Scenario: Outcome is NOT_REQUIRED at or above minimum
- **WHEN** `currentStock` is greater than or equal to `min`
- **THEN** the evaluation result reports `outcome` `NOT_REQUIRED` with no generated tasks

#### Scenario: Outcome is UNAVAILABLE when required but nothing can be allocated
- **WHEN** `currentStock` is below `min` but no reserve has any assignable stock, so `allocatedQuantity` is 0
- **THEN** the evaluation result reports `outcome` `UNAVAILABLE` and `shortfall` equal to `requiredQuantity`

#### Scenario: Outcome is PLANNED when fully covered
- **WHEN** `currentStock` is below `min` and `allocatedQuantity` equals `requiredQuantity`
- **THEN** the evaluation result reports `outcome` `PLANNED` and `shortfall` 0

#### Scenario: Outcome is PARTIALLY_PLANNED when partially covered
- **WHEN** `currentStock` is below `min` and `0 < allocatedQuantity < requiredQuantity`
- **THEN** the evaluation result reports `outcome` `PARTIALLY_PLANNED` and a positive `shortfall`

### Requirement: Reserve allocation is deterministic and greedy
When replenishment is required, the system SHALL allocate `requiredQuantity` from `RESERVE` locations that hold the SKU, ranked by assignable stock descending and breaking ties by `locationCode` ascending. It SHALL create exactly one task per reserve it draws from, allocating `min(assignableStock, remainingRequired)` from each and skipping reserves whose assignable stock is zero, until the required quantity is met or reserves are exhausted.

#### Scenario: Higher assignable reserve is used first
- **WHEN** `SKU-100` requires 95 and `RSV-01` has assignable 60 while `RSV-02` has assignable 50
- **THEN** the system creates a task for 60 from `RSV-01` and a task for 35 from `RSV-02`

#### Scenario: Ties broken by ascending location code
- **WHEN** two reserves have equal assignable stock and only one is needed to satisfy the remaining required quantity
- **THEN** the system draws from the reserve whose `locationCode` sorts first ascending

#### Scenario: Zero-assignable reserve is skipped
- **WHEN** a reserve holds the SKU but its assignable stock is zero
- **THEN** the system creates no task for that reserve and does not include it in the allocation

### Requirement: Partial replenishment exposes shortfall
When the total assignable stock across all reserves is less than `requiredQuantity`, the system SHALL allocate every available assignable unit, generate the corresponding `OPEN` tasks, and report the unmet remainder as `shortfall`. When allocation fully covers the required quantity, `shortfall` SHALL be zero.

#### Scenario: Partial coverage reports shortfall
- **WHEN** `SKU-300` at `PICK-02` requires 110 and the only reserve holds assignable 70
- **THEN** the system creates a task for 70, reports `allocatedQuantity` 70, and reports `shortfall` 40

#### Scenario: Full coverage reports zero shortfall
- **WHEN** the reserves' total assignable stock is greater than or equal to `requiredQuantity`
- **THEN** the system reports `allocatedQuantity` equal to `requiredQuantity` and `shortfall` 0

### Requirement: Open tasks reserve assignable stock dynamically
The system SHALL compute a reserve's assignable stock for a SKU dynamically as `assignable(reserve, sku) = max(0, physicalStock - sum(OPEN task quantities from that reserve for that SKU across all picking locations))`. The result SHALL never be negative. `OPEN` tasks SHALL reduce assignable stock without moving physical stock, so that a later evaluation cannot allocate the same reserve units twice.

#### Scenario: Open task reduces assignable stock for later allocation
- **WHEN** an `OPEN` task already draws 40 of a SKU from a reserve holding 60 physical units and a later evaluation needs that SKU
- **THEN** the later evaluation treats that reserve's assignable stock as 20

#### Scenario: Assignable stock is clamped to zero, never negative
- **WHEN** a reserve holds 30 physical units of a SKU but `OPEN` tasks already draw 50 of that SKU from it (for example because an external movement drained the reserve after the tasks were planned)
- **THEN** the system reports and treats that reserve's assignable stock as 0 rather than a negative value

#### Scenario: Open tasks do not change physical stock
- **WHEN** evaluation generates `OPEN` tasks
- **THEN** the physical stock reported by inventory for both the reserve and picking locations is unchanged until a task is confirmed

### Requirement: Replenishment tasks can be listed
The system SHALL expose `GET /replenishment/tasks` and return HTTP `200` with all tasks in a deterministic order. Each task SHALL expose `id` (a UUID), `sku`, `fromLocation`, `toLocation`, `quantity`, `status`, `createdAt`, and `updatedAt` as minimal traceability.

#### Scenario: List existing tasks
- **WHEN** multiple tasks exist and a client gets `/replenishment/tasks`
- **THEN** the response contains each task exactly once with its `id`, `sku`, `fromLocation`, `toLocation`, `quantity`, `status`, `createdAt`, and `updatedAt` in a deterministic order

#### Scenario: List when no tasks exist
- **WHEN** no tasks have been generated and a client gets `/replenishment/tasks`
- **THEN** the system returns HTTP `200` with an empty array

### Requirement: Replenishment task identifiers are UUIDs
The system SHALL assign each replenishment task a UUID `id` at generation and SHALL treat the `{id}` path variable of the confirm and cancel endpoints as a UUID. A malformed (non-UUID) id SHALL return HTTP `400` with error code `VALIDATION_ERROR` and cause no state change, while a well-formed UUID that does not identify any task SHALL return HTTP `404` with error code `TASK_NOT_FOUND`.

#### Scenario: Generated task id is a UUID
- **WHEN** evaluation generates a task
- **THEN** the task `id` is a UUID and is returned by the listing and confirm/cancel responses

#### Scenario: Malformed id is a validation error, not a missing resource
- **WHEN** a client calls confirm or cancel with a `{id}` that is not a well-formed UUID
- **THEN** the system returns HTTP `400` with error code `VALIDATION_ERROR` rather than HTTP `404`, and changes no task

#### Scenario: Well-formed unknown id is a missing resource
- **WHEN** a client calls confirm or cancel with a well-formed UUID that identifies no task
- **THEN** the system returns HTTP `404` with error code `TASK_NOT_FOUND`

### Requirement: Open tasks can be confirmed atomically
The system SHALL expose `POST /replenishment/tasks/{id}/confirm` to confirm an `OPEN` task. It SHALL validate the task in this order: the `id` is a well-formed UUID (else HTTP `400` `VALIDATION_ERROR`), the task exists (else HTTP `404` `TASK_NOT_FOUND`), the task is `OPEN` (else HTTP `409` `TASK_NOT_OPEN`), the reserve still physically holds at least the task quantity (else HTTP `409` `INSUFFICIENT_STOCK`), and confirming would not overfill the destination beyond its replenishment target (else HTTP `409` `DESTINATION_TARGET_EXCEEDED`). Only when all checks pass SHALL it move the task quantity from the reserve to the picking location using the inventory movement operation and transition the task to `CONFIRMED`, as one all-or-nothing operation. A successful request SHALL return HTTP `200` with the confirmed task.

#### Scenario: Confirm moves stock and closes the task
- **WHEN** a client confirms an `OPEN` task whose reserve still holds sufficient stock and whose destination would not exceed its target
- **THEN** the system moves the task quantity from `fromLocation` to `toLocation`, transitions the task to `CONFIRMED`, and returns HTTP `200`

#### Scenario: Confirm with a malformed task id
- **WHEN** a client confirms a task id that is not a well-formed UUID
- **THEN** the system returns HTTP `400` with error code `VALIDATION_ERROR` and moves no stock

#### Scenario: Confirm an unknown task
- **WHEN** a client confirms a well-formed UUID that does not identify any task
- **THEN** the system returns HTTP `404` with error code `TASK_NOT_FOUND` and moves no stock

#### Scenario: Confirm when reserve stock is no longer sufficient
- **WHEN** a client confirms an `OPEN` task but the reserve's physical stock has since dropped below the task quantity
- **THEN** the system returns HTTP `409` with error code `INSUFFICIENT_STOCK`, leaves the task `OPEN`, and moves no stock

#### Scenario: Confirm a terminal task
- **WHEN** a client confirms a task that is already `CONFIRMED` or `CANCELLED`
- **THEN** the system returns HTTP `409` with error code `TASK_NOT_OPEN` and moves no stock

### Requirement: Confirmation rejects stale tasks that would exceed the replenishment target
The system SHALL revalidate, at confirmation time, that adding the task quantity to the destination picking location's current physical stock would not exceed the rule's `targetQuantity` (`max`) for that `sku` and picking `locationCode`. If `currentDestinationStock + task.quantity` would exceed `targetQuantity`, the system SHALL return HTTP `409` with error code `DESTINATION_TARGET_EXCEEDED`, leave the task `OPEN`, and move no stock. This guards against a task that has become stale (for example because other replenishment already refilled the picking location) overfilling the destination beyond its target. The operator can cancel the stale task and re-evaluate.

#### Scenario: Confirm would overfill the destination beyond its target
- **WHEN** a client confirms an `OPEN` task for quantity `q` but the destination picking location now holds stock such that `currentDestinationStock + q` is greater than the rule's `max`
- **THEN** the system returns HTTP `409` with error code `DESTINATION_TARGET_EXCEEDED`, leaves the task `OPEN`, and moves no stock

#### Scenario: Confirm exactly to the target is allowed
- **WHEN** a client confirms an `OPEN` task for quantity `q` and `currentDestinationStock + q` equals the rule's `max`
- **THEN** the system completes the movement, transitions the task to `CONFIRMED`, and returns HTTP `200`

#### Scenario: Cancel and re-evaluate a stale task
- **WHEN** a task is rejected with `DESTINATION_TARGET_EXCEEDED` and the operator cancels it and re-evaluates the picking location
- **THEN** the cancelled task moves no stock and a fresh evaluation may plan tasks against the current stock

### Requirement: Open tasks can be cancelled without moving stock
The system SHALL expose `POST /replenishment/tasks/{id}/cancel` to cancel an `OPEN` task. It SHALL validate that the `id` is a well-formed UUID (else HTTP `400` `VALIDATION_ERROR`) before transitioning an existing `OPEN` task to `CANCELLED` without moving any physical stock. A successful request SHALL return HTTP `200` with the cancelled task.

#### Scenario: Cancel an open task
- **WHEN** a client cancels an `OPEN` task
- **THEN** the system transitions the task to `CANCELLED`, moves no stock, and returns HTTP `200`

#### Scenario: Cancel with a malformed task id
- **WHEN** a client cancels a task id that is not a well-formed UUID
- **THEN** the system returns HTTP `400` with error code `VALIDATION_ERROR` and moves no stock

#### Scenario: Cancel an unknown task
- **WHEN** a client cancels a well-formed UUID that does not identify any task
- **THEN** the system returns HTTP `404` with error code `TASK_NOT_FOUND`

#### Scenario: Cancel a terminal task
- **WHEN** a client cancels a task that is already `CONFIRMED` or `CANCELLED`
- **THEN** the system returns HTTP `409` with error code `TASK_NOT_OPEN` and moves no stock

### Requirement: Replenishment task FSM restricts transitions
The system SHALL permit only the transitions `OPEN → CONFIRMED` and `OPEN → CANCELLED`. `CONFIRMED` and `CANCELLED` SHALL be terminal, read-only states from which no further transition is allowed. Cancelling an `OPEN` task SHALL release the assignable stock it had reserved, and a `CANCELLED` task no longer draws from its reserve. `CONFIRMED` and `CANCELLED` remain terminal, read-only states.

#### Scenario: Cancelling frees reserved assignable stock
- **WHEN** an `OPEN` task drawing from a reserve is cancelled
- **THEN** that reserve's assignable stock for the SKU increases by the cancelled task quantity for subsequent evaluations

#### Scenario: Confirming frees the open reservation as physical movement
- **WHEN** an `OPEN` task is confirmed
- **THEN** the task no longer counts against assignable stock as an open reservation because the physical stock has actually been debited from the reserve

#### Scenario: No transition from a terminal state
- **WHEN** a client attempts any confirm or cancel on a `CONFIRMED` or `CANCELLED` task
- **THEN** the system returns HTTP `409` with error code `TASK_NOT_OPEN` and does not change the task

### Requirement: Concurrent replenishment operations preserve consistency
The system SHALL serialize rule creation, evaluation, confirmation, and cancellation through the same shared process-wide critical section used by inventory operations, so compound checks and writes behave atomically within one process and assignable stock is never over-allocated.

#### Scenario: Concurrent evaluations do not over-allocate a reserve
- **WHEN** concurrent evaluations for the same SKU at different picking locations would together draw more than a reserve's assignable stock
- **THEN** the combined generated tasks never exceed the reserve's assignable stock and no reserve is allocated below zero assignable

#### Scenario: Concurrent confirmations compete for physical stock
- **WHEN** concurrent confirmations draw from the same reserve for more combined stock than it physically holds
- **THEN** only confirmations supported by the serialized physical stock succeed, the rest return HTTP `409` with error code `INSUFFICIENT_STOCK`, and no partial movement is observable

#### Scenario: Concurrent evaluation is blocked by an in-progress replenishment
- **WHEN** concurrent evaluations target the same SKU and picking location and one generates `OPEN` tasks first
- **THEN** every competing evaluation returns HTTP `409` with error code `REPLENISHMENT_IN_PROGRESS`

### Requirement: Mandatory seed scenario is loaded idempotently in memory
The system SHALL load the mandatory challenge scenario into the in-memory repositories during application startup: picking locations `PICK-01` and `PICK-02`, reserve locations `RSV-01`, `RSV-02`, and `RSV-03`, the three replenishment rules, and the initial stock quants. The seed SHALL be idempotent within one process/context and SHALL NOT duplicate locations, rules, stock, or tasks if it runs again. The seed SHALL NOT persist across restarts.

#### Scenario: Seed loads the challenge scenario
- **WHEN** the application starts
- **THEN** the five locations, three rules, and initial stock quants defined by the challenge are present

#### Scenario: Seed does not duplicate on re-run
- **WHEN** the seed loader runs again within the same process or application context
- **THEN** no duplicate location, rule, stock quant, or task is created and existing values are preserved

#### Scenario: Seed does not persist across restarts
- **WHEN** runtime changes are made and the application is then restarted
- **THEN** those runtime changes do not survive and the seed scenario is present again from a clean in-memory state

### Requirement: Expected replenishment API errors are structured
Every expected replenishment API failure SHALL return JSON containing a stable `code`, a human-readable `message`, and an object-valued `details`. Malformed JSON, invalid enums, missing required values, and Bean Validation failures SHALL return HTTP `400` rather than HTTP `500`. Missing numeric fields SHALL NOT default to zero.

#### Scenario: Business conflict response
- **WHEN** a duplicate rule, in-progress replenishment, terminal-task transition, insufficient-stock, or destination-target-exceeded conflict occurs
- **THEN** the response uses HTTP `409` and contains `code`, `message`, and object-valued `details`

#### Scenario: Missing resource response
- **WHEN** a request references an unknown but syntactically valid location, rule, or task
- **THEN** the response uses HTTP `404` and contains `code`, `message`, and object-valued `details`

#### Scenario: Malformed request response
- **WHEN** a client sends malformed JSON, an invalid enum, or a missing required field
- **THEN** the response uses HTTP `400` with error code `VALIDATION_ERROR` and contains no generic server error
