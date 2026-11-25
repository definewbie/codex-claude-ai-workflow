# Workstream Metrics

- Workstream: ws-20261011T044656Z-BMDJ
- Workflow: agent-lifecycle-terminal-delivery@5
- Status: RUNNING
- Generated At: 2026-10-11T06:49:09.827449Z

## Counts

- Nodes: 19
- Agent Runs: 8
- Gates: 1
- Decisions: 4
- Iterations: 3
- Revisions: 2
- Total Node Duration(ms): 668396

## Longest Nodes

| Node | Status | Iteration | Duration(ms) |
| --- | --- | --- | --- |
| design.claude_design | COMPLETED | iter-002 | 191188 |
| design.claude_design | COMPLETED | iter-003 | 141218 |
| intake.claude_brainstorm | COMPLETED | iter-001 | 87983 |
| intake.claude_brainstorm | COMPLETED | iter-002 | 76681 |
| intake.codex_product_review | COMPLETED | iter-001 | 62522 |

## Provider Metrics

| Provider | Runs | Completed | Failed | Recoverable | Total Duration(ms) | Average Duration(ms) |
| --- | --- | --- | --- | --- | --- | --- |
| claude | 4 | 4 | 0 | 0 | 483526 | 120881 |
| codex | 4 | 4 | 0 | 0 | 162990 | 40747 |
| outcomeStatusCounts | null | null | null | null | null | null |
| executionConsistencyCounts | null | null | null | null | null | null |

## Token Metrics

- Measured Runs: 7 / 8
- Session Snapshots: 0
- Estimated Runs: 0
- Input Tokens: 143594
- Output Tokens: 42835
- Cached Read Tokens: 146304
- Cached Write Tokens: 0
- Total Tokens: 332733
- Estimated Tokens (fallback, not in total): 0

### Token Usage By Step

| Node | Agent | Status | Outcome | Source | Input | Output | Transcript | Context | Prompt Overhead | Adapter Overhead | Total |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| intake.claude_brainstorm | claude | completed | SUCCESS | acpx_stream | 10683 | 6856 | 1964 | 986 | 3336 | 6361 | 22403 |
| intake.codex_product_review | codex | completed | SUCCESS | acpx_stream | 13455 | 1627 | 95317 | 2485 | 3076 | 7894 | 28010 |
| intake.claude_brainstorm | claude | completed | SUCCESS | acpx_stream | 6880 | 6237 | 1099 | 986 | 5033 | 861 | 13117 |
| intake.codex_product_review | codex | completed | SUCCESS | acpx_stream | 13170 | 784 | 61904 | 2485 | 3462 | 7223 | 40194 |
| design.claude_design | claude | completed | SUCCESS | acpx_stream | 41587 | 14063 | 872974 | 0 | 2584 | 39003 | 85538 |
| design.codex_arch_review | codex | completed | SUCCESS | acpx_stream | 38979 | 1168 | 99315 | 6736 | 4085 | 28158 | 53075 |
| design.claude_design | claude | completed | SUCCESS | acpx_stream | 18840 | 12100 | 754941 | 0 | 6136 | 12704 | 90396 |
| design.codex_arch_review | codex | completed | SUCCESS | not_available | 0 | 0 | 80095 | 6736 | 8285 | 0 | 0 |

### Top Context Sections

| Target Node | Profile | Section | Rendered | Original | Degraded | Dropped |
| --- | --- | --- | --- | --- | --- | --- |
| design.codex_arch_review | architecture_review | input_artifacts | 420 | 420 | false | false |
| intake.codex_product_review | product_review | input_artifacts | 405 | 405 | false | false |
| design.codex_arch_review | architecture_review | recent_agent_outputs | 262 | 262 | false | false |
| intake.codex_product_review | product_review | recent_agent_outputs | 184 | 184 | false | false |
| design.codex_arch_review | architecture_review | iteration_history | 153 | 153 | false | false |
| design.codex_arch_review | architecture_review | ledger_pointers | 153 | 139 | true | false |
| intake.claude_brainstorm | intake | ledger_pointers | 153 | 139 | true | false |
| intake.codex_product_review | product_review | ledger_pointers | 153 | 139 | true | false |
| design.codex_arch_review | architecture_review | review_evidence | 66 | 66 | false | false |
| intake.codex_product_review | product_review | review_evidence | 64 | 64 | false | false |

## Tool Metrics

- Tool Calls: 29
- Reads: 29
- Writes: 0
- Bash Commands: 0
- Evidence Files: 0

### Repeated Read Files

| File | Reads | Agents | Nodes | Evidence Known | Reread Reason |
| --- | --- | --- | --- | --- | --- |
| N/A | 0 | N/A | N/A | false | N/A |
