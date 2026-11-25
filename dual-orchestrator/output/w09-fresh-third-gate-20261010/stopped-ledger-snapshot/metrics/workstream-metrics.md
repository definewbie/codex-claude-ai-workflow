# Workstream Metrics

- Workstream: ws-20261010T124812Z-XCZL
- Workflow: agent-lifecycle-terminal-delivery@5
- Status: RUNNING
- Generated At: 2026-10-10T14:16:16.339045Z

## Counts

- Nodes: 13
- Agent Runs: 5
- Gates: 1
- Decisions: 2
- Iterations: 2
- Revisions: 1
- Total Node Duration(ms): 539410

## Longest Nodes

| Node | Status | Iteration | Duration(ms) |
| --- | --- | --- | --- |
| design.claude_design | COMPLETED | iter-002 | 197173 |
| intake.claude_brainstorm | COMPLETED | iter-002 | 178682 |
| intake.claude_brainstorm | COMPLETED | iter-001 | 86954 |
| intake.codex_product_review | COMPLETED | iter-001 | 55977 |
| intake.codex_product_review | COMPLETED | iter-002 | 20209 |

## Provider Metrics

| Provider | Runs | Completed | Failed | Recoverable | Total Duration(ms) | Average Duration(ms) |
| --- | --- | --- | --- | --- | --- | --- |
| claude | 3 | 3 | 0 | 0 | 451373 | 150457 |
| codex | 2 | 2 | 0 | 0 | 70289 | 35144 |
| outcomeStatusCounts | null | null | null | null | null | null |
| executionConsistencyCounts | null | null | null | null | null | null |

## Token Metrics

- Measured Runs: 5 / 5
- Session Snapshots: 0
- Estimated Runs: 0
- Input Tokens: 85729
- Output Tokens: 32773
- Cached Read Tokens: 116864
- Cached Write Tokens: 0
- Total Tokens: 235366
- Estimated Tokens (fallback, not in total): 0

### Token Usage By Step

| Node | Agent | Status | Outcome | Source | Input | Output | Transcript | Context | Prompt Overhead | Adapter Overhead | Total |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| intake.claude_brainstorm | claude | completed | SUCCESS | acpx_stream | 9813 | 5913 | 2090 | 986 | 3336 | 5491 | 20590 |
| intake.codex_product_review | codex | completed | SUCCESS | acpx_stream | 13504 | 1128 | 86425 | 2441 | 3119 | 7944 | 27560 |
| intake.claude_brainstorm | claude | completed | SUCCESS | acpx_stream | 6777 | 10134 | 815 | 986 | 4875 | 916 | 16911 |
| intake.codex_product_review | codex | completed | SUCCESS | acpx_stream | 12636 | 698 | 57003 | 2441 | 3374 | 6821 | 39574 |
| design.claude_design | claude | completed | SUCCESS | acpx_stream | 42999 | 14900 | 925851 | 0 | 2584 | 40415 | 130731 |

### Top Context Sections

| Target Node | Profile | Section | Rendered | Original | Degraded | Dropped |
| --- | --- | --- | --- | --- | --- | --- |
| intake.codex_product_review | product_review | input_artifacts | 409 | 409 | false | false |
| intake.codex_product_review | product_review | recent_agent_outputs | 189 | 189 | false | false |
| intake.claude_brainstorm | intake | ledger_pointers | 153 | 139 | true | false |
| intake.codex_product_review | product_review | ledger_pointers | 153 | 139 | true | false |
| intake.codex_product_review | product_review | review_evidence | 64 | 64 | false | false |
| intake.claude_brainstorm | intake | task_contract | 0 | 0 | false | false |

## Tool Metrics

- Tool Calls: 13
- Reads: 13
- Writes: 0
- Bash Commands: 0
- Evidence Files: 0

### Repeated Read Files

| File | Reads | Agents | Nodes | Evidence Known | Reread Reason |
| --- | --- | --- | --- | --- | --- |
| N/A | 0 | N/A | N/A | false | N/A |
