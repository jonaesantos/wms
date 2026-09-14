---
name: "/adversarial-review"
id: "adversarial-review"
category: "Review"
description: "Review a change adversarially without modifying files"
---

Act as a senior adversarial reviewer. Work in read-only mode and do not modify files.

Compare:

1. The diff from the indicated base point.
2. The corresponding OpenSpec change.
3. `SPECS.md`.
4. `AGENTS.md`.
5. The existing tests.

If the base point or OpenSpec change cannot be inferred safely, ask for it before reviewing.

Review separately:

## Spec Fidelity

- Missing requirements.
- Incorrect behavior.
- Scope creep.
- Inconsistent HTTP errors.
- Untested scenarios.

## Engineering Quality

- Bugs.
- Race conditions.
- Non-atomic movements.
- Stock overallocation.
- Invalid state transitions.
- Incorrect dependencies between modules.
- Domain logic in controllers.
- Tests that validate implementation rather than behavior.
- Unnecessary complexity.

Present findings first, ordered by severity, with `file:line` references. Do not propose cosmetic refactors without concrete impact. If there are no findings, state that explicitly and identify residual risks or testing gaps.
