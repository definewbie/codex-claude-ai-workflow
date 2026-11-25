# Workstream Metrics

- Workstream: ws-20261023T005431Z-9YYF
- Workflow: agent-lifecycle-terminal-delivery@5
- Status: RUNNING
- Generated At: 2026-10-25T22:45:12.667154Z

## Counts

- Nodes: 29
- Agent Runs: 12
- Gates: 3
- Decisions: 5
- Iterations: 3
- Revisions: 2
- Total Node Duration(ms): 1740392

## Longest Nodes

| Node | Status | Iteration | Duration(ms) |
| --- | --- | --- | --- |
| implement.claude_implement | COMPLETED | iter-003 | 518858 |
| plan.claude_plan | COMPLETED | iter-003 | 286212 |
| plan.claude_plan | COMPLETED | iter-002 | 225040 |
| design.claude_design | COMPLETED | iter-001 | 209199 |
| design.claude_design | COMPLETED | iter-002 | 202537 |

## Provider Metrics

| Provider | Runs | Completed | Failed | Recoverable | Total Duration(ms) | Average Duration(ms) |
| --- | --- | --- | --- | --- | --- | --- |
| claude | 6 | 6 | 0 | 0 | 1512144 | 252024 |
| codex | 5 | 5 | 0 | 0 | 195546 | 39109 |
| outcomeStatusCounts | null | null | null | null | null | null |
| executionConsistencyCounts | null | null | null | null | null | null |

## Token Metrics

- Measured Runs: 11 / 11
- Session Snapshots: 0
- Estimated Runs: 0
- Input Tokens: 348253
- Output Tokens: 100119
- Cached Read Tokens: 1318656
- Cached Write Tokens: 0
- Total Tokens: 1767028
- Estimated Tokens (fallback, not in total): 0

### Token Usage By Step

| Node | Agent | Status | Outcome | Source | Input | Output | Transcript | Context | Prompt Overhead | Adapter Overhead | Total |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| intake.claude_brainstorm | claude | completed | SUCCESS | acpx_stream | 12273 | 7015 | 2814 | 1002 | 3359 | 7912 | 24216 |
| intake.codex_product_review | codex | completed | SUCCESS | acpx_current_transcript | 13704 | 914 | 79831 | 2364 | 3283 | 8057 | 27546 |
| design.claude_design | claude | completed | SUCCESS | acpx_current_transcript | 41069 | 11679 | 722739 | 0 | 2600 | 38469 | 82700 |
| design.codex_arch_review | codex | completed | SUCCESS | acpx_current_transcript | 18837 | 1253 | 93245 | 7269 | 4034 | 7534 | 46586 |
| design.claude_design | claude | completed | SUCCESS | acpx_current_transcript | 18771 | 12791 | 783048 | 0 | 6439 | 12332 | 136458 |
| design.codex_arch_review | codex | completed | SUCCESS | acpx_current_transcript | 19376 | 1005 | 86004 | 7269 | 8208 | 3899 | 65565 |
| plan.claude_plan | claude | completed | SUCCESS | acpx_current_transcript | 18473 | 13077 | 797911 | 0 | 6846 | 11627 | 91262 |
| plan.codex_plan_review | codex | completed | SUCCESS | acpx_current_transcript | 24211 | 988 | 91363 | 13032 | 6728 | 4451 | 89583 |
| plan.claude_plan | claude | completed | SUCCESS | acpx_current_transcript | 50434 | 12761 | 789215 | 0 | 13932 | 36502 | 157659 |
| plan.codex_plan_review | codex | completed | SUCCESS | acpx_current_transcript | 37217 | 1002 | 116152 | 13032 | 17268 | 6917 | 126667 |
| implement.claude_implement | claude | completed | SUCCESS | acpx_current_transcript | 93888 | 37634 | 2117792 | 11780 | 14311 | 67797 | 918786 |

### Top Context Sections

| Target Node | Profile | Section | Rendered | Original | Degraded | Dropped |
| --- | --- | --- | --- | --- | --- | --- |
| plan.codex_plan_review | plan_review | input_artifacts | 995 | 1440 | true | false |
| plan.codex_plan_review | plan_review | recent_agent_outputs | 743 | 1284 | true | false |
| implement.claude_implement | implementation | human_feedback | 638 | 638 | false | false |
| implement.claude_implement | implementation | iteration_history | 636 | 636 | false | false |
| plan.codex_plan_review | plan_review | human_feedback | 569 | 569 | false | false |
| plan.codex_plan_review | plan_review | iteration_history | 494 | 636 | true | false |
| design.codex_arch_review | architecture_review | input_artifacts | 453 | 453 | false | false |
| intake.codex_product_review | product_review | input_artifacts | 436 | 436 | false | false |
| design.codex_arch_review | architecture_review | recent_agent_outputs | 294 | 294 | false | false |
| implement.claude_implement | implementation | working_memory | 264 | 264 | false | false |

## Tool Metrics

- Tool Calls: 168
- Reads: 94
- Writes: 40
- Bash Commands: 34
- Evidence Files: 0

### Repeated Read Files

| File | Reads | Agents | Nodes | Evidence Known | Reread Reason |
| --- | --- | --- | --- | --- | --- |
| N/A | 0 | N/A | N/A | false | N/A |
