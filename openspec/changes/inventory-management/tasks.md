## 1. Domain Foundation

- [ ] 1.1 Add Jakarta Bean Validation API to `api` and the Spring validation runtime to `infra`, and verify `./gradlew compileJava` succeeds.
- [ ] 1.2 Add `Sku`, `LocationCode`, location type, location, inventory item, and composite inventory key domain types with strict validation, and verify focused domain tests cover valid, blank, padded, case-sensitive, and negative-quantity cases.
- [ ] 1.3 Add explicit validation, not-found, and conflict domain failures with stable error codes and details, plus location and inventory inbound/outbound ports, and verify the domain module compiles without Spring dependencies.
- [ ] 1.4 Add a WMS API error response, centralized `@RestControllerAdvice`, and strict integer deserialization configuration; verify focused web tests map each domain category and representative deserialization or validation failures to the specified structured errors.

## 2. Location Management

- [ ] 2.1 Implement the location domain service with the shared `Lock`, writing tests first for creation, duplicate rejection, deterministic listing, and concurrent duplicate creation; verify `./gradlew :domain:test` passes.
- [ ] 2.2 Implement the in-memory location repository and configure the one process-wide `ReentrantLock` and location service beans, and verify a Spring context test resolves the service and shared lock.
- [ ] 2.3 Add validated location DTOs, mapping, and documented `POST /locations` and `GET /locations` endpoints, and verify integration tests cover `201`, ordered and empty `200` responses, `LOCATION_ALREADY_EXISTS` `409`, `VALIDATION_ERROR` for invalid enums and padded identifiers, and object-valued error details.

## 3. Stock Establishment And Query

- [ ] 3.1 Implement the inventory repository with composite keys, deterministic SKU lookup, and atomic batch publication support, and verify adapter tests cover replacement, persisted zero, ordering, and complete pair publication.
- [ ] 3.2 Implement absolute stock establishment and SKU query in the inventory domain service, writing tests first for create, replace, zero, unknown location, invalid quantity, unknown SKU, and ordering; verify `./gradlew :domain:test` passes.
- [ ] 3.3 Add stock DTOs with a boxed, required, non-negative `Long` quantity, mapping, and documented `POST /stock` and `GET /stock` endpoints; verify integration tests cover successful upsert/query, missing or invalid numeric values, and exact `400` and `404` errors.

## 4. Atomic Stock Movement

- [ ] 4.1 Implement movement validation and the all-or-nothing debit/credit path with checked `long` arithmetic, writing domain tests first for success, absent destination quant, absent origin quant, insufficient stock, same location, non-positive quantity, unknown locations, and overflow rollback.
- [ ] 4.2 Add concurrent domain tests for competing movements, overlapping reads, and stock replacements, and verify quantities never become negative, total moved stock is conserved, and no partial pair is observable.
- [ ] 4.3 Add the movement DTO with a boxed, required, positive `Long` quantity, response mapping, and documented `POST /stock/move` endpoint; verify integration tests cover the complete success response, missing or invalid numeric values, and exact `400`, `404`, and `409` errors.
- [ ] 4.4 Add concurrent Spring integration tests that exercise the wired services or HTTP endpoints for duplicate location creation and competing movements, and verify the production beans share one lock and preserve the same invariants as domain tests.

## 5. Contract Verification

- [ ] 5.1 Add a contract matrix test that asserts every specified validation, not-found, duplicate, insufficient-stock, and overflow case returns its exact status and error code with `message` and object-valued `details`.
- [ ] 5.2 Add an OpenAPI integration assertion for all five inventory paths; verify required fields, integer formats and bounds, response schemas, and every documented `200`, `201`, `400`, `404`, and `409` status match runtime behavior.
- [ ] 5.3 Run `./gradlew test`, exercise the application startup and Swagger paths with Java 24, and run `openspec validate inventory-management --strict`; resolve every failure before marking the change ready for apply verification.
