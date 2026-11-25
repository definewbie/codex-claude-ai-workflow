# Workstream Summary

- Workstream: ws-20261011T141651Z-MBC2
- Workspace: /private/tmp/dual-w09-newlines-20261011-fresh-05/worktree
- Ledger: /private/tmp/dual-w09-newlines-20261011-fresh-05/worktree/.agent/workstreams/ws-20261011T141651Z-MBC2
- Generated At: 2026-10-11T14:24:29.138512Z

## Workflow

- Definition: agent-lifecycle-terminal-delivery@5
- Status: WAITING_FOR_RECOVERY
- Status Source: workflow_current
- Source Commit: 28
- Active Nodes: node-intake-claude_brainstorm-006
- Open Gates: recovery-node-intake-claude_brainstorm-006
- Updated At: 2026-10-11T14:24:29.051413Z

## Metrics

- Metrics State: CURRENT
- Nodes: 6
- Agent Runs: 3
- Gates: 0
- Decisions: 1
- Iterations: 2
- Revisions: 1
- Total Node Duration(ms): 238939
- Token Runs: 3 / 3
- Total Tokens: 62136 (input 29658, output 14750)
- Metrics: /private/tmp/dual-w09-newlines-20261011-fresh-05/worktree/.agent/workstreams/ws-20261011T141651Z-MBC2/metrics/workstream-metrics.md

## Recommended Next Action

Artifact contract failed

Inspect the diagnostic details, prompt contract, and source output before retrying the node.

Inspect the contract diagnostic and fix the output contract before retrying.

```bash
dualctl recovery show --workdir '/private/tmp/dual-w09-newlines-20261011-fresh-05/worktree' ws-20261011T141651Z-MBC2
dualctl retry --workdir /private/tmp/dual-w09-newlines-20261011-fresh-05/worktree ws-20261011T141651Z-MBC2
```

## Recent Timeline

| Node | Kind | Status | Iteration | Duration |
| --- | --- | --- | --- | --- |
| context.intake_claude_brainstorm | TASK | COMPLETED | iter-001 | 53 |
| intake.claude_brainstorm | TASK | COMPLETED | iter-001 | 91035 |
| context.intake_codex_product_review | TASK | COMPLETED | iter-001 | 104 |
| intake.codex_product_review | TASK | COMPLETED | iter-001 | 59461 |
| intake.decision | DECISION | COMPLETED | iter-001 | 41 |
| intake.claude_brainstorm | TASK | FAILED | iter-002 | 88245 |

## Artifacts

- Final Report: missing
- Current Failure Summary: /private/tmp/dual-w09-newlines-20261011-fresh-05/worktree/.agent/workstreams/ws-20261011T141651Z-MBC2/recovery/current-failure.md
- Delivery: BLOCKED, changedFiles=0, reason=WORKFLOW_NOT_DELIVERABLE:workstream is not completed: WAITING_FOR_RECOVERY
- Calibration Candidates: 0
- Calibration Apply Plans: 0

## Useful Commands

```bash
dualctl status --workdir '/private/tmp/dual-w09-newlines-20261011-fresh-05/worktree' ws-20261011T141651Z-MBC2
dualctl timeline --workdir '/private/tmp/dual-w09-newlines-20261011-fresh-05/worktree' ws-20261011T141651Z-MBC2
dualctl review show --workdir '/private/tmp/dual-w09-newlines-20261011-fresh-05/worktree' ws-20261011T141651Z-MBC2
dualctl recovery show --workdir '/private/tmp/dual-w09-newlines-20261011-fresh-05/worktree' ws-20261011T141651Z-MBC2
```
