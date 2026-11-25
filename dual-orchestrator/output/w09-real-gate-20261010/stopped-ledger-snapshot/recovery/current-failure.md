# Current Failure

## Summary

- Workflow Status: WAITING_FOR_RECOVERY
- Node: intake.claude_brainstorm
- Node Instance: node-intake-claude_brainstorm-002
- Node Status: FAILED
- Handler: acpx.agent_run
- Reason: AGENT_OUTPUT_CONTRACT_VIOLATION
- Recoverable: N/A
- Exit Code: 4
- Run ID: run-1791614610585
- Agent: claude
- Session: claude-main-ws-20261010T055644Z-6QCS-run-1791614610585
- Attention Category: FAILED
- Retry Safe: false

## Contract Failure Diagnostic

- Diagnostic JSON: .agent/workstreams/ws-20261010T055644Z-6QCS/artifacts/contract-failures/node-intake-claude_brainstorm-002.json
- Diagnostic Markdown: .agent/workstreams/ws-20261010T055644Z-6QCS/artifacts/contract-failures/node-intake-claude_brainstorm-002.md
- Error Code: AGENT_RESULT_MISSING
- Error Message: No readable agent output artifact found for contract validation.

## Evidence

- .agent/workstreams/ws-20261010T055644Z-6QCS/runs/run-1791614610585/outputs/intake-claude-brainstorm.stderr.log
- .agent/workstreams/ws-20261010T055644Z-6QCS/artifacts/context-governance/occurrences/node-intake-claude_brainstorm-002-run-1791614610585/binding.json
- .agent/workstreams/ws-20261010T055644Z-6QCS/artifacts/contract-failures/node-intake-claude_brainstorm-002.json
- .agent/workstreams/ws-20261010T055644Z-6QCS/artifacts/contract-failures/node-intake-claude_brainstorm-002.md

## Suggested Action

- Blocking adapter signal: ADAPTER_INCOMPATIBLE - {"source":"acpx.sessions.show","native_version":"UNKNOWN","schema":"acpx.session.v1","acpxRecordId":"1d507713-7c39-44bd-9010-2a15019a4f1e","acpSessionId":"1d507713-7c39-44bd-9010-2a15019a4f1e","cwd":"/private/tmp/dual-w09-newlines-20261010/worktree","name":"claude-main-ws-20261010T055644Z-6QCS-run-1791614610585","protocolVersion":1,"closed":false,"agentCapabilities":{"loadSession":true,"promptCapabilities":{"image":true,"embeddedContext":true}},"compatible":false,"failure":"missing session identity: agentSessionId"}
- Inspect recovery evidence before deciding whether retry is safe.
- Command: `dualctl retry --workdir /private/tmp/dual-w09-newlines-20261010/worktree ws-20261010T055644Z-6QCS  # only after fixing the recorded protocol/session mismatch; never resend an unknown call`.
