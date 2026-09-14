# AGENTS.md

## Architecture

- Respect the existing hexagonal architecture.
- Keep domain independent from Spring and infrastructure concerns.
- Use `domain` for business models, ports and services.
- Use `api` for DTOs and API contracts.
- Use `infra` for controllers, adapters, configuration and persistence implementations.
- Follow the existing User feature only as a placement map, not as a quality baseline.

## Development

- Prefer the smallest implementation that satisfies the OpenSpec requirements.
- Do not introduce abstractions without a concrete domain need.
- Do not implement unspecified behavior without documenting the decision in OpenSpec or an ADR.
- Keep expected business errors explicit and mapped to meaningful HTTP statuses.
- AI-generated code must be reviewed before commit.

## Testing

- Use red-green-refactor for critical domain rules.
- Test behavior, not implementation details.
- Business rules for stock movement, replenishment allocation and task FSM must be covered by domain tests.
- End-to-end API behavior must be verified through integration tests and Swagger/curl.

## Git

- Use Conventional Commits: feat, fix, test, docs, refactor, chore.
- Keep commits focused on one logical change.
- Run relevant tests before committing.
- Do not commit secrets, build outputs or local environment files.

## Definition Of Done

- OpenSpec requirements are implemented.
- Expected HTTP errors are handled explicitly.
- Relevant tests pass.
- `/opsx-verify` has no critical findings.
- Adversarial review has no critical findings for final delivery.
- The implementation can be explained without relying on the AI conversation.
