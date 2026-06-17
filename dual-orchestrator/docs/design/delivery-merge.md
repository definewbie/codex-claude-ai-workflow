# Delivery Merge Design

中文版本：[delivery-merge.zh-CN.md](delivery-merge.zh-CN.md)

## Problem

The default execution mode uses a Git worktree for safety. That protects the
original repository during agent execution, but it leaves a product gap: after a
workstream is accepted, the user still has to manually copy or apply the
worktree diff back to the original repository.

Manual merge commands are fragile. They also make completed worktrees pile up
because the system cannot tell which ones are ready to deliver, already applied,
or safe to clean.

## Goal

Make delivery merge a first-class, explicit post-acceptance action:

```text
accepted worktree -> merge plan -> patch -> apply to original repo -> delivery record
```

The workflow runtime remains terminal after completion. Delivery merge is a
controlled delivery action, not resume, retry, or semantic repair.

## Boundary Model

| Concept | Owner | Meaning |
|---|---|---|
| Source worktree | Git workspace | Sandbox where agents changed files. |
| Target repo | Git workspace | Original repository that receives approved changes. |
| Delivery merge plan | Ledger artifact | Source of truth for merge readiness and result. |
| Patch | Delivery artifact | Exact diff generated from source worktree. |
| CLI / UI | Projection + command surface | Shows status and invokes explicit delivery action. |

The target repo must never be modified by background workflow execution. It is
modified only by an explicit delivery merge command.

## Ledger Shape

```text
.agent/workstreams/<workstream_id>/delivery/
  merge-plan.json
  merge.patch
  changed-files.txt
  excluded-files.txt
  apply.stdout.log
  apply.stderr.log
```

`merge-plan.json`:

```json
{
  "schemaVersion": 1,
  "workstreamId": "ws-...",
  "sourceWorktree": "...",
  "targetRepo": "...",
  "acceptanceDecision": "ACCEPT_WITH_NOTES",
  "changedFiles": [],
  "excludedFiles": [],
  "status": "READY",
  "commit": "",
  "failureReason": ""
}
```

Allowed statuses:

- `READY`: patch generated and target repo is safe to apply.
- `APPLIED`: patch applied to the target repo index/worktree.
- `COMMITTED`: target repo commit recorded.
- `FAILED`: merge preparation or apply failed; logs are preserved.

## Preconditions

Delivery merge is allowed only when:

- workflow status is `COMPLETED`;
- completion audit is ready;
- final acceptance is `ACCEPT` or `ACCEPT_WITH_NOTES`;
- source worktree exists and is a Git worktree;
- target repo exists and is a Git repository;
- target repo working tree is clean;
- source worktree has non-empty business diff.

Default excluded paths:

```text
.agent/
node_modules/
dist/
build/
target/
*.log
```

## CLI

First implementation:

```bash
dualctl delivery merge \
  --workdir <source_worktree> \
  --target <original_repo> \
  <workstream_id>
```

Behavior:

1. read workstream ledger from source worktree;
2. verify completion and acceptance;
3. verify target repo cleanliness;
4. generate changed file list and patch from source worktree;
5. exclude ledger/build artifacts;
6. apply patch to target repo index;
7. write delivery merge plan and logs;
8. print target diff stat and next commit command.

It does not auto-commit and does not auto-clean the worktree.

The first implementation supports tracked business file changes. Untracked
business files fail with a clear message instead of silently changing the source
worktree index. Support for untracked files can be added later with a dedicated
temporary-index implementation.

## Operator Panel

Completed workstreams should show a Delivery section:

```text
Delivery
- Acceptance:
- Changed files:
- Target repo:
- Merge status:
- Last failure:
```

The first UI version may show the command instead of invoking it. Button support
can follow once the CLI and ledger model are stable.

## Tests

Required tests:

- accepted completed workstream applies a source worktree diff to a clean target repo;
- target dirty blocks merge and leaves target unchanged;
- rejected acceptance blocks merge;
- empty patch reports a clear failure;
- excluded files do not enter the target patch;
- patch apply conflict writes stderr and marks the plan failed.

## Review Checklist

- Delivery merge is not implemented inside workflow resume/retry.
- Completed workstreams remain terminal for runtime execution.
- Target repo mutation happens only in explicit delivery command.
- CLI and UI read the same merge plan.
- Failed apply keeps patch and stderr for review.
- Cleanup remains explicit and never runs automatically.
