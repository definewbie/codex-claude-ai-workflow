# Workstream Summary

- Workstream: ws-20261010T055644Z-6QCS
- Workspace: /private/tmp/dual-w09-newlines-20261010/worktree
- Ledger: /private/tmp/dual-w09-newlines-20261010/worktree/.agent/workstreams/ws-20261010T055644Z-6QCS
- Generated At: 2026-10-10T06:43:34.278472Z

## Workflow

- Definition: agent-lifecycle-terminal-delivery@5
- Status: WAITING_FOR_RECOVERY
- Status Source: workflow_current
- Source Commit: 10
- Active Nodes: node-intake-claude_brainstorm-002
- Open Gates: recovery-node-intake-claude_brainstorm-002
- Updated At: 2026-10-10T06:43:34.194101Z

## Metrics

- Metrics State: CURRENT
- Nodes: 2
- Agent Runs: 1
- Gates: 0
- Decisions: 0
- Iterations: 1
- Revisions: 0
- Total Node Duration(ms): 3686
- Token Runs: 0 / 1
- Total Tokens: 0 (input 0, output 0)
- Metrics: /private/tmp/dual-w09-newlines-20261010/worktree/.agent/workstreams/ws-20261010T055644Z-6QCS/metrics/workstream-metrics.md

## Recommended Next Action

Workflow needs inspection

Blocking adapter signal: ADAPTER_INCOMPATIBLE - {"source":"acpx.sessions.show","native_version":"UNKNOWN","schema":"acpx.session.v1","acpxRecordId":"1d507713-7c39-44bd-9010-2a15019a4f1e","acpSessionId":"1d507713-7c39-44bd-9010-2a15019a4f1e","cwd":"/private/tmp/dual-w09-newlines-20261010/worktree","name":"claude-main-ws-20261010T055644Z-6QCS-run-1791614610585","protocolVersion":1,"closed":false,"agentCapabilities":{"loadSession":true,"promptCapabilities":{"image":true,"embeddedContext":true}},"compatible":false,"failure":"missing session identity: agentSessionId"}

Inspect recovery evidence before deciding whether retry is safe.

```bash
dualctl recovery show --workdir '/private/tmp/dual-w09-newlines-20261010/worktree' ws-20261010T055644Z-6QCS
dualctl retry --workdir /private/tmp/dual-w09-newlines-20261010/worktree ws-20261010T055644Z-6QCS  # only after fixing the recorded protocol/session mismatch; never resend an unknown call
```

## Recent Timeline

| Node | Kind | Status | Iteration | Duration |
| --- | --- | --- | --- | --- |
| context.intake_claude_brainstorm | TASK | COMPLETED | iter-001 | 58 |
| intake.claude_brainstorm | TASK | FAILED | iter-001 | 3628 |

## Artifacts

- Final Report: missing
- Current Failure Summary: /private/tmp/dual-w09-newlines-20261010/worktree/.agent/workstreams/ws-20261010T055644Z-6QCS/recovery/current-failure.md
- Delivery: BLOCKED, changedFiles=0, reason=WORKFLOW_NOT_DELIVERABLE:workstream is not completed: WAITING_FOR_RECOVERY
- Calibration Candidates: 0
- Calibration Apply Plans: 0

## Useful Commands

```bash
dualctl status --workdir '/private/tmp/dual-w09-newlines-20261010/worktree' ws-20261010T055644Z-6QCS
dualctl timeline --workdir '/private/tmp/dual-w09-newlines-20261010/worktree' ws-20261010T055644Z-6QCS
dualctl review show --workdir '/private/tmp/dual-w09-newlines-20261010/worktree' ws-20261010T055644Z-6QCS
dualctl recovery show --workdir '/private/tmp/dual-w09-newlines-20261010/worktree' ws-20261010T055644Z-6QCS
```
