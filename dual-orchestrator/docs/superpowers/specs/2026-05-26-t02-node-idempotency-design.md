# T0.2: Node-Level Idempotency Protection

## Problem

`WorkflowRuntime.resume()` reads the active node from the ledger and immediately
executes the handler. Three scenarios are unprotected:

1. **Two terminals, same workstream** — User runs `dualctl resume` in two
   terminals. `WorkstreamLock` (FileLock) prevents concurrent ledger writes, but
   does not prevent the second process from starting a second acpx Agent process.

2. **Ctrl+C during handler execution** — Java process is killed while acpx is
   running. The node remains in READY state in the ledger. On next `resume`, the
   handler re-executes from scratch — but the original acpx process may still be
   alive, producing concurrent Agent activity.

3. **FAILED node auto-retry** — When a node fails due to a recoverable error
   (rate limit, context window exceeded), `resume` rejects and requires manual
   `dualctl retry`. Recoverable errors should auto-retry up to a threshold.

## Design

### Node State Machine (revised)

```
                    initialize
                       │
                       ▼
                    READY ◄── retryActiveNode
                       │
               mark RUNNING(pid)
                       │
                       ▼
                   RUNNING
                  ╱       ╲
      handler success    handler failure
                ╱            ╲
          COMPLETED         FAILED
              │                │
        route to next    ┌─────┴──────┐
         node READY      │            │
                    recoverable?  not recoverable
                         │            │
                    auto-retry     recovery gate
                    (if under      (BLOCKED or
                     threshold)    WAITING_FOR_RECOVERY)
```

### Resume Guard Logic

`WorkflowRuntime.resume()` must check the active node's status before executing:

| Status | Condition | Action |
|---|---|---|
| `READY` | Normal flow | Mark `RUNNING(pid)` → execute handler |
| `RUNNING` | PID dead | Auto-recover: remark `RUNNING(pid)` → execute handler |
| `RUNNING` | PID alive | Reject: "Agent process still running (PID=xxx)" |
| `FAILED` | Recoverable + under threshold | Auto-retry: reset `READY` → mark `RUNNING(pid)` → execute |
| `FAILED` | Not recoverable or over threshold | Reject: "Manual intervention required, use dualctl retry" |
| `BLOCKED` | Revision limit reached | Reject: "Revision limit reached, use dualctl retry or recovery abort" |
| `WAITING` | Gate node | Delegate to gate handler (existing logic) |
| `COMPLETED` | Should not be active | Reject: "Node already completed" |
| `CREATED` / `SKIPPED` | Unused in production | Reject: "Illegal node status" (defensive) |

### RUNNING State with PID Tracking

**When transitioning to RUNNING**, write to the node instance document:

```java
new NodeInstanceDocument(
    ...,
    NodeStatus.RUNNING,
    ...,
    Map.of("running_pid", ProcessHandle.current().pid()),
    ...,
    Instant.now(),  // startedAt
    null            // finishedAt still null
);
```

**PID liveness check** (Java 21+):

```java
boolean isProcessDead(long pid) {
    return ProcessHandle.of(pid)
        .map(ProcessHandle::isAlive)
        .orElse(true);  // PID not found = process dead
}
```

When PID is not found (`orElse(true)`), treat as dead — the OS has recycled the
PID or the process never existed on this machine.

### Auto-Retry for Recoverable Failures

`AcpxFailureClassifier.classify()` stores its result in
`NodeInstanceDocument.result()` under keys:
- `acpx_failure.reason` — e.g. `rate_limit`, `context_window_exceeded`
- `acpx_failure.recoverable` — `true` / `false`

Resume reads these fields. If `recoverable == true` and retry count is under
the workflow's `max_retries` default (default: 3), auto-retry by resetting the
node to READY then immediately proceeding to RUNNING.

Retry count is tracked per-node via `result.auto_retry_count` (incremented on
each auto-retry). This is separate from manual `dualctl retry`.

### Changes Required

**`dual-core` — NodeStatus enum**
- No changes needed. `RUNNING` already exists.

**`dual-ledger-file` — NodeInstanceDocument**
- Add `startedAt` tracking (may already exist, verify).
- Add PID to result map when entering RUNNING.

**`dual-ledger-file` — WorkflowFileLedger**
- New method: `markRunning(paths, nodeInstanceId, pid)` — transitions node
  from READY to RUNNING, writes PID and startedAt.
- New method: `autoRetryNode(paths, nodeInstanceId)` — resets a FAILED node
  to READY for auto-retry, increments `auto_retry_count`.

**`dual-runtime` — WorkflowRuntime.resume()**
- Add status guard before handler dispatch.
- On READY: call `markRunning`, then execute handler.
- On RUNNING with dead PID: call `markRunning` (overwrite), then execute.
- On FAILED with recoverable error under threshold: call `autoRetryNode`, then
  `markRunning`, then execute.
- On all other states: reject with descriptive message.

**`dual-cli` — No command changes**
- The guard is inside `WorkflowRuntime.resume()`, transparent to CLI commands.
- `dualctl resume`, `dualctl run` (via CliWorkflowLoop), all benefit.

### Test Plan

1. **READY → RUNNING → COMPLETED** — Normal flow, verify PID written and
   cleaned up.
2. **RUNNING with dead PID** — Simulate by writing a RUNNING node with a
   non-existent PID, verify auto-recovery.
3. **RUNNING with live PID** — Write a RUNNING node with current process PID,
   verify rejection.
4. **FAILED recoverable auto-retry** — Write a FAILED node with
  `acpx_failure.recoverable=true` and `auto_retry_count=0`, verify auto-retry.
5. **FAILED recoverable over threshold** — Same but `auto_retry_count=3`,
   verify rejection.
6. **FAILED not recoverable** — Write a FAILED node with
   `acpx_failure.recoverable=false`, verify rejection.
7. **BLOCKED** — Verify rejection with correct message.
8. **Two-terminal protection** — Integration test: two resume calls on same
   workstream, verify second is rejected.

### Scope Exclusions

- No changes to `WorkstreamLock` — it continues to protect ledger writes.
- No changes to `CliWorkflowLoop` — the guard is transparent.
- No `--force-resume` flag — PID auto-detection makes it unnecessary for the
  common case. If needed in the future, it can be added as a separate change.
- Auto-retry is only for resume-time FAILED nodes, not for
  `WAITING_FOR_RECOVERY` workflow state (that remains a manual recovery flow).
