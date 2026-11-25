# Workstream Summary

- Workstream: ws-20261010T085617Z-8SVF
- Workspace: /private/tmp/dual-w09-newlines-20261010-fresh-02/worktree
- Ledger: /private/tmp/dual-w09-newlines-20261010-fresh-02/worktree/.agent/workstreams/ws-20261010T085617Z-8SVF
- Generated At: 2026-10-10T09:05:23.220394Z

## Workflow

- Definition: agent-lifecycle-terminal-delivery@5
- Status: WAITING_FOR_RECOVERY
- Status Source: workflow_current
- Source Commit: 10
- Active Nodes: node-intake-claude_brainstorm-002
- Open Gates: recovery-node-intake-claude_brainstorm-002
- Updated At: 2026-10-10T09:05:23.141347Z

## Metrics

- Metrics State: CURRENT
- Nodes: 2
- Agent Runs: 1
- Gates: 0
- Decisions: 0
- Iterations: 1
- Revisions: 0
- Total Node Duration(ms): 438580
- Token Runs: 1 / 1
- Total Tokens: 53096 (input 7900, output 9612)
- Metrics: /private/tmp/dual-w09-newlines-20261010-fresh-02/worktree/.agent/workstreams/ws-20261010T085617Z-8SVF/metrics/workstream-metrics.md

## Recommended Next Action

Artifact contract failed

Inspect the diagnostic details, prompt contract, and source output before retrying the node.

Inspect the contract diagnostic and fix the output contract before retrying.

```bash
dualctl recovery show --workdir '/private/tmp/dual-w09-newlines-20261010-fresh-02/worktree' ws-20261010T085617Z-8SVF
dualctl retry --workdir /private/tmp/dual-w09-newlines-20261010-fresh-02/worktree ws-20261010T085617Z-8SVF
```

## Recent Timeline

| Node | Kind | Status | Iteration | Duration |
| --- | --- | --- | --- | --- |
| context.intake_claude_brainstorm | TASK | COMPLETED | iter-001 | 53 |
| intake.claude_brainstorm | TASK | FAILED | iter-001 | 438527 |

## Artifacts

- Final Report: missing
- Current Failure Summary: /private/tmp/dual-w09-newlines-20261010-fresh-02/worktree/.agent/workstreams/ws-20261010T085617Z-8SVF/recovery/current-failure.md
- Delivery: BLOCKED, changedFiles=0, reason=WORKFLOW_NOT_DELIVERABLE:workstream is not completed: WAITING_FOR_RECOVERY
- Calibration Candidates: 0
- Calibration Apply Plans: 0

## Useful Commands

```bash
dualctl status --workdir '/private/tmp/dual-w09-newlines-20261010-fresh-02/worktree' ws-20261010T085617Z-8SVF
dualctl timeline --workdir '/private/tmp/dual-w09-newlines-20261010-fresh-02/worktree' ws-20261010T085617Z-8SVF
dualctl review show --workdir '/private/tmp/dual-w09-newlines-20261010-fresh-02/worktree' ws-20261010T085617Z-8SVF
dualctl recovery show --workdir '/private/tmp/dual-w09-newlines-20261010-fresh-02/worktree' ws-20261010T085617Z-8SVF
```
