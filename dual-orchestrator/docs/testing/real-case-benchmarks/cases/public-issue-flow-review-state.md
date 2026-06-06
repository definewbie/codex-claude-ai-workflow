# Public Benchmark Case: Issue Flow Review State

## Task

Add a `READY_FOR_REVIEW` status to the issue workflow.

## Background

Read the public sample app:

- `README.md`
- `docs/product.md`
- `src/issueWorkflow.js`
- `src/dashboard.js`
- `test/issueWorkflow.test.js`

The product team wants a review checkpoint between implementation and done.
Today an issue can move directly from `IN_PROGRESS` to `DONE`. That is too loose
for teams that require human review before completion.

## Requirements

1. Add a new `READY_FOR_REVIEW` status.
2. Allow `IN_PROGRESS -> READY_FOR_REVIEW`.
3. Allow `READY_FOR_REVIEW -> IN_PROGRESS` when review asks for rework.
4. Allow `READY_FOR_REVIEW -> DONE` when review passes.
5. Do not allow `OPEN -> READY_FOR_REVIEW`.
6. Do not allow `BLOCKED -> READY_FOR_REVIEW`.
7. Update the dashboard summary so counts include `READY_FOR_REVIEW`.
8. Keep blocked issue listing behavior unchanged.
9. Update docs so users understand the new review checkpoint.
10. Add or update tests for valid transitions, invalid transitions, and summary counts.

## Constraints

- Keep workflow rules explicit.
- Do not add external dependencies.
- Do not replace the current transition model with a framework.
- Do not remove existing statuses or existing valid transitions except the direct
  `IN_PROGRESS -> DONE` path if the implementation decides review should be
  mandatory.
- Preserve existing public function names unless a change is clearly necessary.

## Expected Self-test

Claude should decide how to test, but the sample app supports:

```bash
npm test
```

or:

```bash
node --test
```

The self-test report should say which command was run, whether it passed, and
what behavior was covered.
