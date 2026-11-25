# Workstream Metrics

- Workstream: ws-20261014T234009Z-RN9X
- Workflow: agent-lifecycle-terminal-delivery@5
- Status: WAITING_FOR_RECOVERY
- Generated At: 2026-10-14T23:45:06.507282Z

## Counts

- Nodes: 6
- Agent Runs: 3
- Gates: 0
- Decisions: 1
- Iterations: 2
- Revisions: 1
- Total Node Duration(ms): 206879

## Longest Nodes

| Node | Status | Iteration | Duration(ms) |
| --- | --- | --- | --- |
| intake.claude_brainstorm | FAILED | iter-002 | 121306 |
| intake.claude_brainstorm | COMPLETED | iter-001 | 43223 |
| intake.codex_product_review | COMPLETED | iter-001 | 42127 |
| context.intake_codex_product_review | COMPLETED | iter-001 | 126 |
| context.intake_claude_brainstorm | COMPLETED | iter-001 | 57 |

## Provider Metrics

| Provider | Runs | Completed | Failed | Recoverable | Total Duration(ms) | Average Duration(ms) |
| --- | --- | --- | --- | --- | --- | --- |
| claude | 2 | 1 | 1 | 1 | 157761 | 78880 |
| codex | 1 | 1 | 0 | 0 | 36982 | 36982 |
| outcomeStatusCounts | null | null | null | null | null | null |
| executionConsistencyCounts | null | null | null | null | null | null |

## Token Metrics

- Measured Runs: 2 / 3
- Session Snapshots: 0
- Estimated Runs: 0
- Input Tokens: 18185
- Output Tokens: 4974
- Cached Read Tokens: 12928
- Cached Write Tokens: 0
- Total Tokens: 36087
- Estimated Tokens (fallback, not in total): 0

### Token Usage By Step

| Node | Agent | Status | Outcome | Source | Input | Output | Transcript | Context | Prompt Overhead | Adapter Overhead | Total |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| intake.claude_brainstorm | claude | completed | SUCCESS | acpx_stream | 4906 | 3753 | 1334 | 986 | 3343 | 577 | 8659 |
| intake.codex_product_review | codex | completed | SUCCESS | acpx_current_transcript | 13279 | 1221 | 83950 | 2210 | 3184 | 7885 | 27428 |
| intake.claude_brainstorm | claude | failed | OUTPUT_INCOMPLETE | not_available | 0 | 0 | 1693 | 986 | 4997 | 0 | 0 |

### Top Context Sections

| Target Node | Profile | Section | Rendered | Original | Degraded | Dropped |
| --- | --- | --- | --- | --- | --- | --- |
| intake.codex_product_review | product_review | input_artifacts | 406 | 406 | false | false |
| intake.codex_product_review | product_review | recent_agent_outputs | 186 | 186 | false | false |
| intake.claude_brainstorm | intake | ledger_pointers | 153 | 139 | true | false |
| intake.codex_product_review | product_review | ledger_pointers | 153 | 139 | true | false |
| intake.codex_product_review | product_review | review_evidence | 64 | 64 | false | false |
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
