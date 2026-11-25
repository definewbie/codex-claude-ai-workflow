# Contract Failure Diagnostic

- Category: missing_agent_result
- Reason: AGENT_RESULT_MISSING
- Node: intake.claude_brainstorm
- Node Instance: node-intake-claude_brainstorm-002
- Recommended Owner: agent_or_prompt

## Message

No readable agent output artifact found for contract validation.

## Recovery Hint

Ask the agent step to emit one strict AgentResultEnvelope JSON object as its final assistant message for intake.claude_brainstorm.

## Contract Shape

- Required Keys: [summary, decision]
- Allowed Decisions: [READY_FOR_PRODUCT_REVIEW]
