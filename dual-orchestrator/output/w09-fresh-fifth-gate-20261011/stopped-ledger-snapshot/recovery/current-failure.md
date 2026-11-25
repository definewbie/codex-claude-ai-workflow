# Current Failure

## Summary

- Workflow Status: WAITING_FOR_RECOVERY
- Node: intake.claude_brainstorm
- Node Instance: node-intake-claude_brainstorm-006
- Node Status: FAILED
- Handler: acpx.agent_run
- Reason: required_artifact_contract_failed
- Recovery Category: ARTIFACT_CONTRACT_FAILURE
- Retry Policy: MANUAL_ONLY
- Exit Code: 0
- Run ID: run-1791728580831
- Agent: claude
- Session: claude-main-ws-20261011T141651Z-MBC2-run-1791728580831
- Attention Category: ARTIFACT_CONTRACT_FAILURE
- Retry Safe: false

## Contract Failure Diagnostic

- Diagnostic JSON: .agent/workstreams/ws-20261011T141651Z-MBC2/artifacts/contract-failures/node-intake-claude_brainstorm-006.json
- Diagnostic Markdown: .agent/workstreams/ws-20261011T141651Z-MBC2/artifacts/contract-failures/node-intake-claude_brainstorm-006.md
- Error Code: TASK_CONTRACT_REVISION_PATCH_INVALID
- Error Message: task contract revision patch could not be merged
- Details:
  - errors: [REVISION_BASE_PROPOSAL_SHA256_MISMATCH]

## Evidence

- .agent/workstreams/ws-20261011T141651Z-MBC2/runs/run-1791728580831/outputs/intake-claude-brainstorm.stderr.log
- .agent/workstreams/ws-20261011T141651Z-MBC2/runs/run-1791728580831/outputs/intake-claude-brainstorm.transcript.log
- .agent/workstreams/ws-20261011T141651Z-MBC2/runs/run-1791728580831/outputs/intake-claude-brainstorm.json
- .agent/workstreams/ws-20261011T141651Z-MBC2/artifacts/context-governance/occurrences/node-intake-claude_brainstorm-006-run-1791728580831/binding.json
- .agent/workstreams/ws-20261011T141651Z-MBC2/artifacts/contract-failures/node-intake-claude_brainstorm-006.json
- .agent/workstreams/ws-20261011T141651Z-MBC2/artifacts/contract-failures/node-intake-claude_brainstorm-006.md

## Suggested Action

- Inspect the diagnostic details, prompt contract, and source output before retrying the node.
- Inspect the contract diagnostic and fix the output contract before retrying.
- Command: `dualctl retry --workdir /private/tmp/dual-w09-newlines-20261011-fresh-05/worktree ws-20261011T141651Z-MBC2`.
