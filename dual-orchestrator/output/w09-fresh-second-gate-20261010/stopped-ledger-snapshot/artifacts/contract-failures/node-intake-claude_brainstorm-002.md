# Contract Failure Diagnostic

- Category: contract_violation
- Reason: TASK_CONTRACT_EVIDENCE_TRACEABILITY_INVALID
- Node: intake.claude_brainstorm
- Node Instance: node-intake-claude_brainstorm-002
- Source Output: .agent/workstreams/ws-20261010T085617Z-8SVF/runs/run-1791622684632/outputs/intake-claude-brainstorm.json
- Recommended Owner: harness_or_prompt

## Message

task_contract_proposal is not grounded in the tracked inventory

## Recovery Hint

Inspect the diagnostic details, prompt contract, and source output before retrying the node.

## Contract Shape

- Required Keys: [summary, decision]
- Allowed Decisions: [READY_FOR_PRODUCT_REVIEW]
