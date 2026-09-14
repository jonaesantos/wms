## Context

See `proposal.md` for motivation and `specs/inventory-management/spec.md` for observable behavior. The template already separates plain Java domain code, API DTOs, infrastructure adapters, and root integration tests, but its User example uses generic exceptions, controller-local error handling, documentation-only validation, and non-atomic check-then-save flows. Inventory introduces compound writes that must remain consistent across in-memory repositories and later replenishment operations.

## Goals / Non-Goals

**Goals:**

- Preserve the existing module dependency direction and keep business rules in plain Java services.
- Establish reusable location and inventory ports for the later replenishment change.
- Guarantee process-local atomicity for uniqueness checks, stock replacement, movement, and reads.
- Make validation, error classification, ordering, and arithmetic behavior deterministic.

**Non-Goals:**

- Add database transactions, distributed locks, asynchronous processing, or persistence across restarts.
- Reserve stock or trigger replenishment when stock changes.
- Model products, units of measure, location capacity, lots, pallets, or movement history.
- Add replenishment rules, tasks, their FSM, or the mandatory seed; those form the planned `replenishment-workflow` change.
- Remove or refactor the template's User and Hello examples in this change.

## Decisions

### Keep domain and use-case code in the domain module

Add `Sku` and `LocationCode` value objects, `Location` and `InventoryItem` models, inbound service interfaces, outbound repository interfaces, and plain Java service implementations under `domain`. DTOs remain in `api`; controllers, mappers, in-memory adapters, exception advice, and Spring wiring remain in `infra`.

This follows the template's placement while avoiding its quality gaps. Putting rules in controllers was rejected because it couples behavior to HTTP and prevents isolated domain tests.

### Use a shared reentrant critical section

Create one process-wide `ReentrantLock` in Spring configuration and inject the same `Lock` into location and inventory domain services. Every public service operation acquires and releases it with `try/finally`; the later replenishment service will receive this same instance. Reentrancy allows replenishment confirmation to call the inventory movement operation without deadlocking.

A `ConcurrentHashMap` alone was rejected because thread-safe individual calls do not make duplicate checks or debit-plus-credit sequences atomic. Per-repository or per-SKU locks were rejected as unnecessary complexity for a small single-process challenge and would make cross-repository ordering harder to prove.

### Represent stock by a composite value key

The inventory repository addresses quants by the immutable pair `Sku + LocationCode`. An absent quant is interpreted as zero inside a location-specific domain operation, but SKU queries return only persisted quants. Repository list results are sorted at the service boundary to keep behavior deterministic regardless of map iteration order.

No SKU master is introduced because the challenge only requires SKU identifiers on quants and rules.

### Commit movements through one repository operation

The inventory service validates identifiers, both locations, distinct endpoints, positive quantity, sufficient origin stock, and destination arithmetic before persistence. It computes both resulting immutable quants with `Math.subtractExact` and `Math.addExact`, then passes the pair to one repository batch operation while holding the shared lock. The in-memory adapter publishes the complete updated state without exposing an intermediate write.

Sequential controller calls or independent debit and credit repository methods were rejected because they can expose partial state or require compensation. Destination overflow is an expected conflict; all validation occurs before the batch write.

### Guard against destination overflow (defensive decision beyond the challenge)

The challenge does not mention overflow, and with signed `long` a real credit reaching `Long.MAX_VALUE` is practically unreachable. We still validate the destination credit with `Math.addExact` and return HTTP `409` with error code `STOCK_OVERFLOW` to protect the non-negative and consistent stock invariant and to avoid a silent wraparound into a negative quantity. This is our defensive decision, not a core business rule; it costs one checked operation and one mapped error.

### Establish stock as an absolute upsert

`POST /stock` replaces the quantity for a composite key, including zero. This follows the challenge wording, which says to establish available stock. Increment semantics were rejected because they make retries ambiguous and overlap with the explicit movement operation.

`POST /stock` returns HTTP `200` even when it creates a quant that did not exist, because the operation establishes or replaces an absolute value rather than creating a new addressable resource identity; `201` was rejected because there is no new resource URL and repeated calls are idempotent replacements.

### Query stock by SKU only in this slice

`GET /stock` supports only the `?sku` query parameter here. The challenge's optional `?location={code}` filter is deliberately out of scope for `inventory-management`; it can be added later without changing the persisted model.

### Use typed domain failures and centralized HTTP mapping

Domain services throw explicit validation, not-found, and conflict exceptions with stable error codes and details but no Spring types. A single `@RestControllerAdvice` maps these categories to `400`, `404`, and `409`, and also handles malformed JSON, invalid enums, and Bean Validation errors. Unexpected exceptions are not downgraded to expected client errors.

The `api` module receives Jakarta Validation annotations for DTO constraints, while `infra` receives the Spring validation runtime. Request quantities use boxed `Long` fields with `@NotNull` so a missing value cannot become a valid primitive zero. Jackson is configured not to coerce floating-point JSON numbers into integers; out-of-range values remain deserialization failures. Controller methods use `@Valid`, and OpenAPI annotations describe the same required fields and bounds.

Controller-local `try/catch` blocks and `ResponseEntity<?>` were rejected because they duplicate policy and can misclassify defects.

### Return explicit mutation results

Location creation returns the created location. Stock replacement returns the resulting quant. Movement returns the SKU, moved quantity, and the complete resulting origin and destination quants. These responses make Swagger and manual verification useful without requiring follow-up reads.

## Risks / Trade-offs

- [A global lock serializes unrelated SKUs and reads] -> Accept the throughput cost for a small in-memory single-process service; replace it with database transactions and constraints if persistence changes.
- [Repository methods could be called without the service lock] -> Keep adapters behind domain ports and route seed and HTTP operations through inbound services; document the service boundary as the consistency boundary.
- [In-memory state disappears on restart] -> This is allowed for core scope and will be made obvious in the final README.
- [Strict case and whitespace handling can surprise clients] -> Document the behavior in OpenAPI and return a precise validation error instead of silently changing identifiers.
- [The existing User API retains weaker error behavior temporarily] -> Keep this change focused; remove the sample in the planned cleanup after functional changes are archived.

## Migration Plan

No persisted data migration is needed. Add the new vertical slice alongside the User example, run domain and integration tests before the separate seed-bearing change, and verify the five new API paths through OpenAPI and a local application run. Rollback consists of removing the new slice and dependency additions because no external persisted state is introduced.

This slice ships no seed data. The mandatory seed (locations, stock, and replenishment rules together) lives entirely in the planned `replenishment-workflow` change because it depends on rules that do not exist yet here. `inventory-management` is therefore demonstrated standalone through Swagger or `curl` against its five endpoints rather than from a preloaded scenario.
