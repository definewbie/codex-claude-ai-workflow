# Issue Flow App

This is a small public benchmark application for dual-orchestrator. It is not a
toy single-function fixture: it has product rules, domain state transitions,
summary data, and tests that an Agent can use as self-test evidence.

## Domain

The app models a lightweight issue workflow:

- `OPEN`: the issue is new.
- `IN_PROGRESS`: someone is working on it.
- `BLOCKED`: the issue cannot move forward.
- `DONE`: the issue is complete.

Allowed transitions are intentionally conservative:

- `OPEN -> IN_PROGRESS`
- `IN_PROGRESS -> BLOCKED`
- `BLOCKED -> IN_PROGRESS`
- `IN_PROGRESS -> DONE`

The sample dashboard summarizes issue counts by status and lists blocked issues.

## Run Tests

```bash
node --test
```

## Files

- `src/issueWorkflow.js`: state transition rules and issue mutation.
- `src/dashboard.js`: summary projection used by the dashboard.
- `test/issueWorkflow.test.js`: workflow and dashboard tests.
- `docs/product.md`: product rules used by benchmark tasks.
