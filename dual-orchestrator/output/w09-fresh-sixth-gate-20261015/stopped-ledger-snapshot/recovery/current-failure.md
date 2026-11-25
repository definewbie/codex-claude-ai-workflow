# Current Failure

## Summary

- Workflow Status: WAITING_FOR_RECOVERY
- Node: intake.claude_brainstorm
- Node Instance: node-intake-claude_brainstorm-006
- Node Status: FAILED
- Handler: acpx.agent_run
- Reason: agent_result_missing_or_invalid
- Recovery Category: OUTPUT_INCOMPLETE
- Retry Policy: MANUAL_ONLY
- Exit Code: 0
- Run ID: run-1792021385197
- Agent: claude
- Session: claude-main-ws-20261014T234009Z-RN9X-run-1792021385197
- Attention Category: RUNTIME_RECOVERY
- Retry Safe: false

## Secondary Contract Diagnostic

These acquisition diagnostics do not replace the execution outcome's primary reason.

- Diagnostic JSON: .agent/workstreams/ws-20261014T234009Z-RN9X/artifacts/contract-failures/node-intake-claude_brainstorm-006.json
- Diagnostic Markdown: .agent/workstreams/ws-20261014T234009Z-RN9X/artifacts/contract-failures/node-intake-claude_brainstorm-006.md
- Error Code: AGENT_RESULT_PARSE_FAILED
- Error Message: Unrecognized field "response" (class dev.dual.runtime.handler.AgentResultEnvelope$RevisionResponse), not marked as ignorable (2 known properties: "resolved_by_clause_ids", "review_obligation_id"])
 at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 56, column: 22] (through reference chain: dev.dual.runtime.handler.AgentResultEnvelope["result"]->dev.dual.runtime.handler.AgentResultEnvelope$TaskContractProposalResult["revision_responses"]->java.util.ArrayList[0]->dev.dual.runtime.handler.AgentResultEnvelope$RevisionResponse["response"])

## Evidence

- .agent/workstreams/ws-20261014T234009Z-RN9X/runs/run-1792021385197/outputs/intake-claude-brainstorm.stderr.log
- .agent/workstreams/ws-20261014T234009Z-RN9X/runs/run-1792021385197/outputs/intake-claude-brainstorm.transcript.log
- .agent/workstreams/ws-20261014T234009Z-RN9X/runs/run-1792021385197/outputs/intake-claude-brainstorm.json
- .agent/workstreams/ws-20261014T234009Z-RN9X/artifacts/context-governance/occurrences/node-intake-claude_brainstorm-006-run-1792021385197/binding.json
- .agent/workstreams/ws-20261014T234009Z-RN9X/artifacts/contract-failures/node-intake-claude_brainstorm-006.json
- .agent/workstreams/ws-20261014T234009Z-RN9X/artifacts/contract-failures/node-intake-claude_brainstorm-006.md

## Suggested Action

- The final ACP assistant message did not contain a valid AgentResultEnvelope.
- Inspect the agent output contract, then retry the same active node.
- Command: `dualctl retry --workdir /private/tmp/dual-w09-newlines-20261015-fresh-06/worktree ws-20261014T234009Z-RN9X`.
