# Workstream Metrics

- Workstream: ws-20261010T055644Z-6QCS
- Workflow: agent-lifecycle-terminal-delivery@5
- Status: WAITING_FOR_RECOVERY
- Generated At: 2026-10-10T06:43:34.223084Z

## Counts

- Nodes: 2
- Agent Runs: 1
- Gates: 0
- Decisions: 0
- Iterations: 1
- Revisions: 0
- Total Node Duration(ms): 3686

## Longest Nodes

| Node | Status | Iteration | Duration(ms) |
| --- | --- | --- | --- |
| intake.claude_brainstorm | FAILED | iter-001 | 3628 |
| context.intake_claude_brainstorm | COMPLETED | iter-001 | 58 |

## Provider Metrics

| Provider | Runs | Completed | Failed | Recoverable | Total Duration(ms) | Average Duration(ms) |
| --- | --- | --- | --- | --- | --- | --- |
| claude | 1 | 0 | 1 | 1 | 113 | 113 |
| outcomeStatusCounts | null | null | null | null | null | null |
| executionConsistencyCounts | null | null | null | null | null | null |

## Token Metrics

- Measured Runs: 0 / 1
- Session Snapshots: 0
- Estimated Runs: 0
- Input Tokens: 0
- Output Tokens: 0
- Cached Read Tokens: 0
- Cached Write Tokens: 0
- Total Tokens: 0
- Estimated Tokens (fallback, not in total): 0

### Token Usage By Step

| Node | Agent | Status | Outcome | Source | Input | Output | Transcript | Context | Prompt Overhead | Adapter Overhead | Total |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| intake.claude_brainstorm | claude | failed | RECOVERABLE_ADAPTER_FAILURE | not_available | 0 | 0 | 0 | 984 | 3199 | 0 | 0 |

### Top Context Sections

| Target Node | Profile | Section | Rendered | Original | Degraded | Dropped |
| --- | --- | --- | --- | --- | --- | --- |
| intake.claude_brainstorm | intake | ledger_pointers | 151 | 130 | true | false |
| intake.claude_brainstorm | intake | task_contract | 0 | 0 | false | false |

## Tool Metrics

- Tool Calls: 0
- Reads: 0
- Writes: 0
- Bash Commands: 0
- Evidence Files: 0

### Repeated Read Files

| File | Reads | Agents | Nodes | Evidence Known | Reread Reason |
| --- | --- | --- | --- | --- | --- |
| N/A | 0 | N/A | N/A | false | N/A |
