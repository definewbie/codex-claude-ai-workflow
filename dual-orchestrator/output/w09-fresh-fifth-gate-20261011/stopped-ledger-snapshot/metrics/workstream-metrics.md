# Workstream Metrics

- Workstream: ws-20261011T141651Z-MBC2
- Workflow: agent-lifecycle-terminal-delivery@5
- Status: WAITING_FOR_RECOVERY
- Generated At: 2026-10-11T14:24:29.081718Z

## Counts

- Nodes: 6
- Agent Runs: 3
- Gates: 0
- Decisions: 1
- Iterations: 2
- Revisions: 1
- Total Node Duration(ms): 238939

## Longest Nodes

| Node | Status | Iteration | Duration(ms) |
| --- | --- | --- | --- |
| intake.claude_brainstorm | COMPLETED | iter-001 | 91035 |
| intake.claude_brainstorm | FAILED | iter-002 | 88245 |
| intake.codex_product_review | COMPLETED | iter-001 | 59461 |
| context.intake_codex_product_review | COMPLETED | iter-001 | 104 |
| context.intake_claude_brainstorm | COMPLETED | iter-001 | 53 |

## Provider Metrics

| Provider | Runs | Completed | Failed | Recoverable | Total Duration(ms) | Average Duration(ms) |
| --- | --- | --- | --- | --- | --- | --- |
| claude | 2 | 1 | 1 | 1 | 172448 | 86224 |
| codex | 1 | 1 | 0 | 0 | 54272 | 54272 |
| outcomeStatusCounts | null | null | null | null | null | null |
| executionConsistencyCounts | null | null | null | null | null | null |

## Token Metrics

- Measured Runs: 3 / 3
- Session Snapshots: 0
- Estimated Runs: 0
- Input Tokens: 29658
- Output Tokens: 14750
- Cached Read Tokens: 17728
- Cached Write Tokens: 0
- Total Tokens: 62136
- Estimated Tokens (fallback, not in total): 0

### Token Usage By Step

| Node | Agent | Status | Outcome | Source | Input | Output | Transcript | Context | Prompt Overhead | Adapter Overhead | Total |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| intake.claude_brainstorm | claude | completed | SUCCESS | acpx_stream | 9214 | 6479 | 2961 | 986 | 3336 | 4892 | 20493 |
| intake.codex_product_review | codex | completed | SUCCESS | acpx_current_transcript | 13491 | 1372 | 98749 | 2318 | 3267 | 7906 | 27791 |
| intake.claude_brainstorm | claude | failed | CONTRACT_FAILED | acpx_stream | 6953 | 6899 | 974 | 986 | 5109 | 858 | 13852 |

### Top Context Sections

| Target Node | Profile | Section | Rendered | Original | Degraded | Dropped |
| --- | --- | --- | --- | --- | --- | --- |
| intake.codex_product_review | product_review | input_artifacts | 402 | 402 | false | false |
| intake.codex_product_review | product_review | recent_agent_outputs | 181 | 181 | false | false |
| intake.claude_brainstorm | intake | ledger_pointers | 153 | 139 | true | false |
| intake.codex_product_review | product_review | ledger_pointers | 153 | 139 | true | false |
| intake.codex_product_review | product_review | review_evidence | 64 | 64 | false | false |
| intake.claude_brainstorm | intake | task_contract | 0 | 0 | false | false |

## Tool Metrics

- Tool Calls: 2
- Reads: 2
- Writes: 0
- Bash Commands: 0
- Evidence Files: 0

### Repeated Read Files

| File | Reads | Agents | Nodes | Evidence Known | Reread Reason |
| --- | --- | --- | --- | --- | --- |
| N/A | 0 | N/A | N/A | false | N/A |
