# Current Failure

## Summary

- Workflow Status: WAITING_FOR_RECOVERY
- Node: intake.claude_brainstorm
- Node Instance: node-intake-claude_brainstorm-002
- Node Status: FAILED
- Handler: acpx.agent_run
- Reason: session_bootstrap_failed
- Recovery Category: RECOVERABLE_ADAPTER_FAILURE
- Retry Policy: MANUAL_ONLY
- Exit Code: 4
- Run ID: run-1792682394836
- Agent: claude
- Session: claude-main-ws-20261022T150557Z-Y2KB-run-1792682394836
- Attention Category: RUNTIME_RECOVERY
- Retry Safe: false

## Secondary Contract Diagnostic

These acquisition diagnostics do not replace the execution outcome's primary reason.

- Diagnostic JSON: .agent/workstreams/ws-20261022T150557Z-Y2KB/artifacts/contract-failures/node-intake-claude_brainstorm-002.json
- Diagnostic Markdown: .agent/workstreams/ws-20261022T150557Z-Y2KB/artifacts/contract-failures/node-intake-claude_brainstorm-002.md
- Error Code: AGENT_RESULT_MISSING
- Error Message: No readable agent output artifact found for contract validation.

## Evidence

- .agent/workstreams/ws-20261022T150557Z-Y2KB/runs/run-1792682394836/outputs/intake-claude-brainstorm.stderr.log
- .agent/workstreams/ws-20261022T150557Z-Y2KB/artifacts/context-governance/occurrences/node-intake-claude_brainstorm-002-run-1792682394836/binding.json
- .agent/workstreams/ws-20261022T150557Z-Y2KB/artifacts/contract-failures/node-intake-claude_brainstorm-002.json
- .agent/workstreams/ws-20261022T150557Z-Y2KB/artifacts/contract-failures/node-intake-claude_brainstorm-002.md

## Suggested Action

- Blocking adapter signal: SESSION_BOOTSTRAP_FAILED
- Inspect the session bootstrap failure before retrying the same active node.
- Command: `dualctl retry --workdir /private/tmp/dual-w09-newlines-20261022-offline-07/worktree ws-20261022T150557Z-Y2KB  # only after inspecting the session bootstrap failure`.
