# Workstream Metrics

- Workstream: ws-20261023T005431Z-9YYF
- Workflow: agent-lifecycle-terminal-delivery@5
- Status: WAITING_FOR_HUMAN
- Generated At: 2026-10-26T01:22:00.200832Z

## Counts

- Nodes: 37
- Agent Runs: 13
- Gates: 4
- Decisions: 7
- Iterations: 3
- Revisions: 2
- Total Node Duration(ms): 1977117

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
| claude | 7 | 7 | 0 | 0 | 1680351 | 240050 |
| codex | 6 | 6 | 0 | 0 | 248702 | 41450 |
| outcomeStatusCounts | null | null | null | null | null | null |
| executionConsistencyCounts | null | null | null | null | null | null |

## Token Metrics

- Measured Runs: 13 / 13
- Session Snapshots: 0
- Estimated Runs: 0
- Input Tokens: 402903
- Output Tokens: 111796
- Cached Read Tokens: 1563008
- Cached Write Tokens: 0
- Total Tokens: 2077707
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
| implement.claude_self_test_report | claude | completed | SUCCESS | acpx_current_transcript | 51885 | 10419 | 643091 | 0 | 12256 | 39629 | 261472 |
| acceptance.codex_verifier_review | codex | completed | SUCCESS | acpx_current_transcript | 2765 | 1258 | 180308 | 6159 | 3896 | 0 | 49207 |

### Top Context Sections

| Target Node | Profile | Section | Rendered | Original | Degraded | Dropped |
| --- | --- | --- | --- | --- | --- | --- |
| acceptance.codex_verifier_review | acceptance | input_artifacts | 1242 | 1242 | false | false |
| plan.codex_plan_review | plan_review | input_artifacts | 995 | 1440 | true | false |
| acceptance.codex_verifier_review | acceptance | recent_agent_outputs | 771 | 771 | true | false |
| plan.codex_plan_review | plan_review | recent_agent_outputs | 743 | 1284 | true | false |
| acceptance.codex_verifier_review | acceptance | human_feedback | 638 | 638 | false | false |
| implement.claude_implement | implementation | human_feedback | 638 | 638 | false | false |
| implement.claude_implement | implementation | iteration_history | 636 | 636 | false | false |
| plan.codex_plan_review | plan_review | human_feedback | 569 | 569 | false | false |
| acceptance.codex_verifier_review | acceptance | task_contract | 501 | 501 | false | false |
| acceptance.codex_verifier_review | acceptance | git_evidence | 494 | 578 | true | false |

## Tool Metrics

- Tool Calls: 213
- Reads: 114
- Writes: 40
- Bash Commands: 59
- Evidence Files: 0

### Repeated Read Files

| File | Reads | Agents | Nodes | Evidence Known | Reread Reason |
| --- | --- | --- | --- | --- | --- |
| N/A | 0 | N/A | N/A | false | N/A |
