# Workstream Metrics

- Workstream: ws-20261010T085617Z-8SVF
- Workflow: agent-lifecycle-terminal-delivery@5
- Status: WAITING_FOR_RECOVERY
- Generated At: 2026-10-10T09:05:23.164175Z

## Counts

- Nodes: 2
- Agent Runs: 1
- Gates: 0
- Decisions: 0
- Iterations: 1
- Revisions: 0
- Total Node Duration(ms): 438580

## Longest Nodes

| Node | Status | Iteration | Duration(ms) |
| --- | --- | --- | --- |
| intake.claude_brainstorm | FAILED | iter-001 | 438527 |
| context.intake_claude_brainstorm | COMPLETED | iter-001 | 53 |

## Provider Metrics

| Provider | Runs | Completed | Failed | Recoverable | Total Duration(ms) | Average Duration(ms) |
| --- | --- | --- | --- | --- | --- | --- |
| claude | 1 | 0 | 1 | 1 | 434495 | 434495 |
| outcomeStatusCounts | null | null | null | null | null | null |
| executionConsistencyCounts | null | null | null | null | null | null |

## Token Metrics

- Measured Runs: 1 / 1
- Session Snapshots: 0
- Estimated Runs: 0
- Input Tokens: 7900
- Output Tokens: 9612
- Cached Read Tokens: 35584
- Cached Write Tokens: 0
- Total Tokens: 53096
- Estimated Tokens (fallback, not in total): 0

### Token Usage By Step

| Node | Agent | Status | Outcome | Source | Input | Output | Transcript | Context | Prompt Overhead | Adapter Overhead | Total |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| intake.claude_brainstorm | claude | failed | CONTRACT_FAILED | agent_result_acquisition | 7900 | 9612 | 3687 | 986 | 3201 | 3713 | 53096 |

### Top Context Sections

| Target Node | Profile | Section | Rendered | Original | Degraded | Dropped |
| --- | --- | --- | --- | --- | --- | --- |
| intake.claude_brainstorm | intake | ledger_pointers | 153 | 139 | true | false |
| intake.claude_brainstorm | intake | task_contract | 0 | 0 | false | false |

## Tool Metrics

- Tool Calls: 3
- Reads: 3
- Writes: 0
- Bash Commands: 0
- Evidence Files: 0

### Repeated Read Files

| File | Reads | Agents | Nodes | Evidence Known | Reread Reason |
| --- | --- | --- | --- | --- | --- |
| N/A | 0 | N/A | N/A | false | N/A |
