# Current Failure

## Summary

- Workflow Status: WAITING_FOR_RECOVERY
- Node: intake.claude_brainstorm
- Node Instance: node-intake-claude_brainstorm-012
- Node Status: FAILED
- Handler: acpx.agent_run
- Reason: AGENT_OUTPUT_CONTRACT_VIOLATION
- Recoverable: N/A
- Exit Code: N/A
- Run ID: N/A
- Agent: N/A
- Session: N/A
- Attention Category: RUNTIME_RECOVERY
- Retry Safe: false

## Contract Failure Diagnostic

- Diagnostic JSON: .agent/workstreams/ws-20260913T004742Z-6NX5/artifacts/contract-failures/node-intake-claude_brainstorm-012.json
- Diagnostic Markdown: .agent/workstreams/ws-20260913T004742Z-6NX5/artifacts/contract-failures/node-intake-claude_brainstorm-012.md
- Error Code: AGENT_RESULT_MISSING
- Error Message: No readable agent output artifact found for contract validation.

## Evidence

- .agent/workstreams/ws-20260913T004742Z-6NX5/artifacts/contract-failures/node-intake-claude_brainstorm-012.json
- .agent/workstreams/ws-20260913T004742Z-6NX5/artifacts/contract-failures/node-intake-claude_brainstorm-012.md

## Suggested Action

- The final ACP assistant message did not contain a valid AgentResultEnvelope.
- Inspect the agent output contract, then retry the same active node.
- Command: `dualctl retry --workdir /Users/kriswang/work/projects/ai/okx-btc-k-line-demo-agent-runs/ws-20260913T004742Z-6NX5 ws-20260913T004742Z-6NX5`.
