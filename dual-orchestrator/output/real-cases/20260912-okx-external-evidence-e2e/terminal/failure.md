# Current Failure

## Summary

- Workflow Status: WAITING_FOR_RECOVERY
- Node: design.claude_design
- Node Instance: node-design-claude_design-036
- Node Status: FAILED
- Handler: acpx.agent_run
- Reason: AGENT_OUTPUT_CONTRACT_VIOLATION
- Recoverable: N/A
- Exit Code: 0
- Run ID: run-1789171318596
- Agent: claude
- Session: claude-main-ws-20260911T231749Z-GCTW-run-1789171318596
- Attention Category: RUNTIME_RECOVERY
- Retry Safe: false

## Contract Failure Diagnostic

- Diagnostic JSON: .agent/workstreams/ws-20260911T231749Z-GCTW/artifacts/contract-failures/node-design-claude_design-036.json
- Diagnostic Markdown: .agent/workstreams/ws-20260911T231749Z-GCTW/artifacts/contract-failures/node-design-claude_design-036.md
- Error Code: AGENT_RESULT_CONTRACT_VIOLATION
- Error Message: AgentResultEnvelope failed contract validation: [AUTHORED_RESPONSE_ID_INVALID]
- Details:
  - errors: [AUTHORED_RESPONSE_ID_INVALID]

## Evidence

- .agent/workstreams/ws-20260911T231749Z-GCTW/runs/run-1789171318596/outputs/design-claude.stderr.log
- .agent/workstreams/ws-20260911T231749Z-GCTW/runs/run-1789171318596/outputs/design-claude.transcript.log
- .agent/workstreams/ws-20260911T231749Z-GCTW/runs/run-1789171318596/outputs/design-claude.json
- .agent/workstreams/ws-20260911T231749Z-GCTW/artifacts/context-governance/occurrences/node-design-claude_design-036-run-1789171318596/binding.json
- .agent/workstreams/ws-20260911T231749Z-GCTW/artifacts/contract-failures/node-design-claude_design-036.json
- .agent/workstreams/ws-20260911T231749Z-GCTW/artifacts/contract-failures/node-design-claude_design-036.md

## Suggested Action

- The final ACP assistant message did not contain a valid AgentResultEnvelope.
- Inspect the agent output contract, then retry the same active node.
- Command: `dualctl retry --workdir /Users/kriswang/work/projects/ai/okx-btc-k-line-demo-agent-runs/ws-20260911T231749Z-GCTW ws-20260911T231749Z-GCTW`.
