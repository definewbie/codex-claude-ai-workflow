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

The product team wants a mandatory review checkpoint between implementation and
done. Today an issue can move directly from `IN_PROGRESS` to `DONE`. That is too
loose for this team, because every completed issue must be reviewed before it
can be marked done.

## Requirements

1. Add a new `READY_FOR_REVIEW` status.
2. Allow `IN_PROGRESS -> READY_FOR_REVIEW`.
3. Allow `READY_FOR_REVIEW -> IN_PROGRESS` when review asks for rework.
4. Allow `READY_FOR_REVIEW -> DONE` when review passes.
5. Do not allow `OPEN -> READY_FOR_REVIEW`.
6. Do not allow `BLOCKED -> READY_FOR_REVIEW`.
7. Remove the direct `IN_PROGRESS -> DONE` transition. Review is mandatory.
8. Update the dashboard summary so counts include `READY_FOR_REVIEW`.
9. Keep blocked issue listing behavior unchanged.
10. Update docs so users understand the mandatory review checkpoint.
11. Add or update tests for valid transitions, invalid transitions, and summary counts.

## Constraints

- Keep workflow rules explicit.
- Do not add external dependencies.
- Do not replace the current transition model with a framework.
- Do not remove existing statuses.
- The direct `IN_PROGRESS -> DONE` transition must be removed as part of making
  review mandatory.
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
