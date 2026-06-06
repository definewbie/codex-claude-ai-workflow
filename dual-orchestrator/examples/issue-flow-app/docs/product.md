# Product Notes

The issue workflow is used by a small product team to track work from intake to
completion. Users care about two things:

1. Whether an issue can legally move to the next state.
2. Whether the dashboard explains where work is stuck.

## Current Rules

- New issues start as `OPEN`.
- Work starts by moving an issue from `OPEN` to `IN_PROGRESS`.
- Work can be marked `BLOCKED` when a dependency or decision is missing.
- Blocked work can return to `IN_PROGRESS`.
- Finished work moves from `IN_PROGRESS` to `DONE`.

## Design Constraints

- Keep workflow rules explicit and easy to inspect.
- Do not silently create unknown statuses.
- Dashboard summaries must be derived from issue data, not hand-written totals.
- Tests should cover both valid and invalid state transitions.
