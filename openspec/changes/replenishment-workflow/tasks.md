## 1. Domain Foundation

- [ ] 1.1 Add the `ReplenishmentRule` model (`Sku`, `LocationCode`, `min`, `max`) with strict validation, and verify focused domain tests cover valid construction, `0 <= min <= max`, and rejection of negative or `min > max` thresholds.
- [ ] 1.2 Add the `ReplenishmentTaskStatus` enum (`OPEN`, `CONFIRMED`, `CANCELLED`), a `ReplenishmentTaskId` value object backed by a UUID (generated with `UUID.randomUUID()`), and the `ReplenishmentTask` model (`id`, `sku`, `fromLocation`, `toLocation`, `quantity`, `status`, `createdAt`, `updatedAt`), and verify focused domain tests cover construction from valid values, UUID id generation, and rejection of a non-positive quantity.
- [ ] 1.3 Add a `ReplenishmentEvaluation` result value object exposing `sku`, `locationCode`, `currentStock`, `min`, `max`, `targetQuantity`, `requiredQuantity`, `allocatedQuantity`, `shortfall`, `outcome` (`NOT_REQUIRED`/`UNAVAILABLE`/`PLANNED`/`PARTIALLY_PLANNED`), and generated tasks, and verify a domain test asserts `targetQuantity == max`, `requiredQuantity == max - currentStock`, and the outcome derivation.
- [ ] 1.4 Add the new stable error codes (`RULE_ALREADY_EXISTS`, `RULE_NOT_FOUND`, `LOCATION_NOT_PICKING`, `TASK_NOT_FOUND`, `TASK_NOT_OPEN`, `REPLENISHMENT_IN_PROGRESS`, `DESTINATION_TARGET_EXCEEDED`) and the inbound `ReplenishmentService` and outbound `ReplenishmentRuleRepository`/`ReplenishmentTaskRepository` ports, and verify the domain module compiles without Spring dependencies.

## 2. Replenishment Rules

- [ ] 2.1 Implement rule creation in the replenishment domain service using the shared `Lock`, writing tests first for successful creation, duplicate rejection (`RULE_ALREADY_EXISTS`), non-picking location (`LOCATION_NOT_PICKING`), unknown location (`LOCATION_NOT_FOUND`), and `min > max` / negative threshold (`VALIDATION_ERROR`); verify `./gradlew :domain:test` passes.
- [ ] 2.2 Implement the in-memory `ReplenishmentRuleRepository` adapter and verify adapter tests cover save, duplicate-key lookup by `sku`+`locationCode`, and absence lookup.
- [ ] 2.3 Add validated rule DTOs (boxed, required, non-negative `Long` `min`/`max`), mapping, and a documented `POST /replenishment-rules` endpoint, and verify integration tests cover `201`, `RULE_ALREADY_EXISTS` `409`, `LOCATION_NOT_PICKING` `400`, `LOCATION_NOT_FOUND` `404`, `min > max` `400`, and object-valued error details.

## 3. Evaluation And Assignable Stock

- [ ] 3.1 Implement dynamic assignable-stock computation (`max(0, physical - sum(OPEN task quantities for that reserve+SKU across all pickings))`), writing domain tests first that assert an open task reduces assignable stock, that assignable is clamped to `0` (never negative) when physical is below the open-allocated quantity, and that physical stock is unchanged while tasks are `OPEN`.
- [ ] 3.2 Implement evaluation trigger, quantity math, and outcome (tasks only when `currentStock < min`; none when `== min` or `> min`; `targetQuantity = max`, `requiredQuantity = max - currentStock`; `outcome` `NOT_REQUIRED` at/above min), writing domain tests first for below-min, at-min, and above-min cases including the `NOT_REQUIRED` outcome.
- [ ] 3.3 Implement deterministic greedy reserve allocation (rank by assignable descending, tie-break `locationCode` ascending, one task per reserve, skip zero-assignable), writing domain tests first for single-reserve coverage, multi-reserve ordering, and the ascending-code tie-break.
- [ ] 3.4 Implement partial replenishment, `shortfall`, and the required-path outcomes, writing domain tests first for full coverage (`PLANNED`, `shortfall` 0), partial coverage (`PARTIALLY_PLANNED`, `allocatedQuantity` < `requiredQuantity`, positive `shortfall`), and required-but-nothing-assignable (`UNAVAILABLE`, `allocatedQuantity` 0).
- [ ] 3.5 Implement the ordered evaluation validation (location exists `404 LOCATION_NOT_FOUND`, then type `PICKING` `400 LOCATION_NOT_PICKING`, then rule exists `404 RULE_NOT_FOUND`) and the `REPLENISHMENT_IN_PROGRESS` guard (reject evaluation when an `OPEN` task exists for the same SKU+picking), writing domain tests first that assert each failure in order and that no tasks are created.
- [ ] 3.6 Add evaluation DTOs (including the `outcome` field), mapping, and a documented `POST /replenishment/tasks` endpoint returning `200`, and verify integration tests cover task generation with the correct `outcome`, empty `NOT_REQUIRED` result at/above min, `LOCATION_NOT_FOUND` `404`, `LOCATION_NOT_PICKING` `400` for a reserve location, `RULE_NOT_FOUND` `404`, and `REPLENISHMENT_IN_PROGRESS` `409`.

## 4. Task Listing And FSM

- [ ] 4.1 Implement the in-memory `ReplenishmentTaskRepository` and deterministic task listing, and verify adapter/domain tests cover ordering, single occurrence per task, and the empty case.
- [ ] 4.2 Implement task confirmation using the shared `Lock` and `InventoryService.moveStock` with ordered validation (id is a well-formed UUID, task exists, task `OPEN`, reserve stock sufficient, destination not overfilled beyond `max`), writing domain tests first for a successful atomic move plus `OPEN → CONFIRMED`, well-formed unknown id (`TASK_NOT_FOUND` `404`), insufficient reserve stock (`INSUFFICIENT_STOCK` `409`, task stays `OPEN`, no movement), a stale task whose destination would exceed the target (`DESTINATION_TARGET_EXCEEDED` `409`, task stays `OPEN`, no movement) plus the exact-to-target boundary succeeding, and terminal-task rejection (`TASK_NOT_OPEN` `409`).
- [ ] 4.3 Implement task cancellation, writing domain tests first for `OPEN → CANCELLED` with no movement, released assignable stock after cancel, `TASK_NOT_FOUND` `404`, and terminal-task rejection (`TASK_NOT_OPEN` `409`).
- [ ] 4.4 Add a documented `GET /replenishment/tasks` (UUID `id` in responses) and the `POST /replenishment/tasks/{id}/confirm` and `POST /replenishment/tasks/{id}/cancel` endpoints (typed UUID `{id}`) with a `Clock`-driven `createdAt`/`updatedAt`, and verify integration tests cover the confirm/cancel success responses, a malformed (non-UUID) id returning `400` `VALIDATION_ERROR` on both confirm and cancel, a well-formed unknown UUID returning `404` `TASK_NOT_FOUND` on both, and the exact `200`, `409` errors.

## 5. Concurrency

- [ ] 5.1 Add domain concurrency tests proving competing evaluations for the same SKU at different pickings never over-allocate a reserve's assignable stock and never drive assignable below zero.
- [ ] 5.2 Add domain concurrency tests proving competing confirmations from the same reserve move no more than physical stock, that losers return `INSUFFICIENT_STOCK` `409`, and that no partial movement is observable.
- [ ] 5.3 Add Spring integration tests exercising the wired replenishment and inventory services, and verify they share one lock so evaluation, confirmation, and cancellation preserve the same invariants as the domain tests.

## 6. Seed And Wiring

- [ ] 6.1 Add `ReplenishmentConfiguration` wiring the `ReplenishmentDomainService` with the existing shared `Lock` and `InventoryService` plus the new repositories, and add a `Clock` bean, verifying a Spring context test resolves the service and shares the single lock instance.
- [ ] 6.2 Add an idempotent in-memory startup seed loader that creates the challenge locations, rules, and stock through the domain services, and verify an integration test asserts the seeded scenario is present and that running the loader twice creates no duplicate location, rule, stock quant, or task.
- [ ] 6.3 Document in the README how to run the app and load/verify the seed, and verify the documented commands reproduce the seeded scenario.

## 7. Contract Verification

- [ ] 7.1 Add a contract matrix test asserting every specified rule, evaluation, confirm, and cancel error returns its exact status and error code with `message` and object-valued `details`.
- [ ] 7.2 Add an OpenAPI integration assertion for all five replenishment paths, verifying required fields, integer formats/bounds, response schemas, and every documented `200`, `201`, `400`, `404`, and `409` status matches runtime behavior.
- [ ] 7.3 Run `./gradlew test`, exercise application startup and the Swagger/curl replenishment flow with Java 24, and run `openspec validate replenishment-workflow --strict`; resolve every failure before marking the change ready for apply verification.
